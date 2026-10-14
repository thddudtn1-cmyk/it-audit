package com.itaudit.inspection;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.itaudit.auth.LoginUser;
import com.itaudit.common.BusinessException;
import com.itaudit.evidence.EvidenceOwnerType;
import com.itaudit.evidence.EvidenceService;
import com.itaudit.improvement.ImprovementService;
import com.itaudit.request.AuditRequestService;
import com.itaudit.request.RequestStatus;
import com.itaudit.round.RoundItem;

/** 내 할 일, 점검 입력 (항목 상세) */
@Controller
public class InspectionController {

    private final InspectionService service;
    private final EvidenceService evidenceService;
    private final AuditRequestService requestService;
    private final ImprovementService improvementService;

    public InspectionController(InspectionService service, EvidenceService evidenceService,
                                AuditRequestService requestService, ImprovementService improvementService) {
        this.service = service;
        this.evidenceService = evidenceService;
        this.requestService = requestService;
        this.improvementService = improvementService;
    }

    @GetMapping("/my-tasks")
    public String myTasks(@AuthenticationPrincipal LoginUser user, Model model) {
        List<RoundItem> items = service.findMyItems(user);
        // 회신불필요 항목은 입력 대상이 아니므로 '입력 필요'에서 뺀다
        model.addAttribute("pending", items.stream().filter(i -> i.isInputOpen() && !i.isNoReply()).toList());
        model.addAttribute("done", items.stream().filter(i -> !i.isInputOpen() || i.isNoReply()).toList());
        model.addAttribute("requests", requestService.findMine(user).stream()
                .filter(r -> r.getStatus() == RequestStatus.REQUESTED).toList());
        model.addAttribute("improvements", improvementService.findMyOpen(user));
        return "inspection/my-tasks";
    }

    @GetMapping("/inspections/{roundItemId}")
    public String view(@PathVariable Long roundItemId, @AuthenticationPrincipal LoginUser user, Model model) {
        InspectionService.Target t = service.getForView(roundItemId, user);
        return render(model, t, user, InspectionForm.of(t.item()));
    }

    @PostMapping("/inspections/{roundItemId}")
    public String save(@PathVariable Long roundItemId, @ModelAttribute("form") InspectionForm form,
                       @RequestParam(name = "files", required = false) List<MultipartFile> files,
                       @RequestParam(defaultValue = "save") String action,
                       @AuthenticationPrincipal LoginUser user, Model model, RedirectAttributes ra) {
        boolean submit = "submit".equals(action);
        try {
            service.save(roundItemId, form, files, submit, user);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage()
                    + (files != null && files.stream().anyMatch(f -> !f.isEmpty()) ? " (선택한 파일은 다시 골라 주세요)" : ""));
            return render(model, service.getForView(roundItemId, user), user, form);
        }
        ra.addFlashAttribute("message", submit ? "제출했습니다." : "임시저장했습니다.");
        return submit ? "redirect:/my-tasks" : "redirect:/inspections/" + roundItemId;
    }

    private String render(Model model, InspectionService.Target t, LoginUser user, InspectionForm form) {
        model.addAttribute("item", t.item());
        model.addAttribute("round", t.round());
        model.addAttribute("form", form);
        model.addAttribute("results", InspectionResult.values());
        model.addAttribute("editable", service.canEdit(t, user));
        model.addAttribute("blockReason", service.editBlockReason(t, user));
        model.addAttribute("evidences", evidenceService.findActive(EvidenceOwnerType.ROUND_ITEM, t.item().getRoundItemId()));
        model.addAttribute("history", service.findHistory(t));
        // F-10: 같은 항목의 개선사항·권고사항 이력
        model.addAttribute("improvementHistory", improvementService.findHistory(t.item().getSeqNo()));
        return "inspection/form";
    }
}
