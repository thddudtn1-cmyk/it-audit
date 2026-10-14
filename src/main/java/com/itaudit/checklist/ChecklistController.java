package com.itaudit.checklist;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.itaudit.auth.LoginUser;
import com.itaudit.common.BusinessException;
import com.itaudit.improvement.ImprovementService;
import com.itaudit.round.RoundMapper;

import jakarta.servlet.http.HttpSession;

/** 체크리스트 관리 화면 (주관자, 관리자) */
@Controller
@RequestMapping("/checklist")
public class ChecklistController {

    static final String SESSION_UPLOAD = "checklistUploadPreview";

    private final ChecklistService service;
    private final ChecklistExcelParser parser;
    private final RoundMapper roundMapper;
    private final ImprovementService improvementService;

    public ChecklistController(ChecklistService service, ChecklistExcelParser parser, RoundMapper roundMapper,
                               ImprovementService improvementService) {
        this.service = service;
        this.parser = parser;
        this.roundMapper = roundMapper;
        this.improvementService = improvementService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Integer year,
                       @RequestParam(required = false) AuditArea area,
                       @RequestParam(required = false) AuditPeriod period,
                       Model model) {
        List<Integer> years = service.findYears();
        int selected = year != null ? year : (years.isEmpty() ? LocalDate.now().getYear() : years.get(0));
        List<ChecklistItem> items = service.findItems(selected, area, period);

        model.addAttribute("years", years);
        model.addAttribute("year", selected);
        model.addAttribute("area", area);
        model.addAttribute("period", period);
        model.addAttribute("areas", AuditArea.values());
        model.addAttribute("periods", AuditPeriod.values());
        model.addAttribute("rows", service.toRows(items));
        model.addAttribute("totalCount", service.countByYear(selected));
        model.addAttribute("auditItemCount", items.stream().map(i -> i.getAuditArea() + "|" + i.getAuditItem()).distinct().count());
        model.addAttribute("nextYear", (years.isEmpty() ? selected : years.get(0)) + 1);
        return "checklist/list";
    }

    // ---------- 엑셀 업로드 ----------

    @GetMapping("/upload")
    public String uploadForm(@RequestParam(required = false) Integer year, Model model) {
        model.addAttribute("year", year != null ? year : LocalDate.now().getYear());
        return "checklist/upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam int year, @RequestParam("file") MultipartFile file,
                         HttpSession session, Model model) {
        model.addAttribute("year", year);
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (file.isEmpty()) {
            model.addAttribute("errorMessage", "파일을 선택하세요.");
            return "checklist/upload";
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            model.addAttribute("errorMessage", ".xlsx 파일만 올릴 수 있습니다.");
            return "checklist/upload";
        }
        try {
            ChecklistService.checkYear(year);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "checklist/upload";
        }
        ChecklistUploadPreview preview;
        try (InputStream in = file.getInputStream()) {
            preview = parser.parse(in, fileName, year);
        } catch (IOException e) {
            model.addAttribute("errorMessage", "파일을 읽지 못했습니다.");
            return "checklist/upload";
        }
        preview.setExistingCount(service.countByYear(year));
        preview.setUsedInRounds(service.isUsedInRounds(year));
        session.setAttribute(SESSION_UPLOAD, preview);
        model.addAttribute("preview", preview);
        return "checklist/upload-preview";
    }

    @PostMapping("/upload/confirm")
    public String confirmUpload(@AuthenticationPrincipal LoginUser actor, HttpSession session, RedirectAttributes ra) {
        ChecklistUploadPreview preview = (ChecklistUploadPreview) session.getAttribute(SESSION_UPLOAD);
        if (preview == null) {
            ra.addFlashAttribute("errorMessage", "업로드 내용이 만료되었습니다. 파일을 다시 올리세요.");
            return "redirect:/checklist/upload";
        }
        try {
            int count = service.saveUpload(preview, actor);
            session.removeAttribute(SESSION_UPLOAD);
            ra.addFlashAttribute("message", preview.getApplyYear() + "년 체크리스트 " + count + "건을 등록했습니다.");
            return "redirect:/checklist?year=" + preview.getApplyYear();
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/checklist/upload?year=" + preview.getApplyYear();
        }
    }

    @PostMapping("/upload/cancel")
    public String cancelUpload(HttpSession session) {
        ChecklistUploadPreview preview = (ChecklistUploadPreview) session.getAttribute(SESSION_UPLOAD);
        session.removeAttribute(SESSION_UPLOAD);
        return preview == null ? "redirect:/checklist" : "redirect:/checklist?year=" + preview.getApplyYear();
    }

    // ---------- 개정본 복사 ----------

    @PostMapping("/copy")
    public String copy(@RequestParam int fromYear, @RequestParam int toYear,
                       @AuthenticationPrincipal LoginUser actor, RedirectAttributes ra) {
        try {
            int count = service.copyYear(fromYear, toYear, actor);
            ra.addFlashAttribute("message", fromYear + "년 체크리스트 " + count + "건을 복사해 " + toYear + "년 개정본을 만들었습니다.");
            return "redirect:/checklist?year=" + toYear;
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/checklist?year=" + fromYear;
        }
    }

    // ---------- 개별 등록·수정 ----------

    @GetMapping("/items/new")
    public String newItem(@RequestParam int year, Model model) {
        return itemForm(model, new ChecklistForm(), year, null);
    }

    @PostMapping("/items")
    public String create(@RequestParam int year,
                         @Validated @ModelAttribute("form") ChecklistForm form, BindingResult binding,
                         @AuthenticationPrincipal LoginUser actor, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            return itemForm(model, form, year, null);
        }
        try {
            service.create(year, form, actor);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return itemForm(model, form, year, null);
        }
        ra.addFlashAttribute("message", "순번 " + form.getSeqNo() + " 항목을 등록했습니다.");
        return "redirect:/checklist?year=" + year;
    }

    @GetMapping("/items/{itemId}/edit")
    public String editItem(@PathVariable Long itemId, Model model) {
        ChecklistItem item = service.getItem(itemId);
        return itemForm(model, ChecklistForm.of(item), item.getApplyYear(), itemId);
    }

    @PostMapping("/items/{itemId}")
    public String update(@PathVariable Long itemId,
                         @Validated @ModelAttribute("form") ChecklistForm form, BindingResult binding,
                         @AuthenticationPrincipal LoginUser actor, Model model, RedirectAttributes ra) {
        ChecklistItem item = service.getItem(itemId);
        if (binding.hasErrors()) {
            return itemForm(model, form, item.getApplyYear(), itemId);
        }
        try {
            service.update(itemId, form, actor);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return itemForm(model, form, item.getApplyYear(), itemId);
        }
        ra.addFlashAttribute("message", "순번 " + form.getSeqNo() + " 항목을 수정했습니다.");
        return "redirect:/checklist?year=" + item.getApplyYear();
    }

    /**
     * F-10 항목 이력: 같은 순번 항목의 모든 회차 점검결과와 개선사항·권고사항.
     * [가정] 순번을 연도와 관계없는 마스터 코드로 보고 연도를 넘어 같은 항목으로 묶는다 (F-03 배정 복사와 같은 기준).
     */
    @GetMapping("/items/{itemId}/history")
    public String history(@PathVariable Long itemId, Model model) {
        ChecklistItem item = service.getItem(itemId);
        model.addAttribute("item", item);
        model.addAttribute("rounds", roundMapper.findAllBySeqNo(item.getSeqNo()));
        model.addAttribute("improvements", improvementService.findHistory(item.getSeqNo()));
        return "checklist/history";
    }

    private String itemForm(Model model, ChecklistForm form, int year, Long itemId) {
        model.addAttribute("form", form);
        model.addAttribute("year", year);
        model.addAttribute("itemId", itemId);
        model.addAttribute("areas", AuditArea.values());
        model.addAttribute("periods", AuditPeriod.values());
        model.addAttribute("auditItems", service.findAuditItems(year));
        model.addAttribute("regulations", service.findRegulations(year));
        return "checklist/form";
    }
}
