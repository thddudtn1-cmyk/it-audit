package com.itaudit.round;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.itaudit.auth.LoginUser;
import com.itaudit.common.BusinessException;

/** 분기 회차 목록·생성, 점검 총괄표(회차 상세), 배정 편집 */
@Controller
@RequestMapping("/rounds")
public class RoundController {

    private final RoundService service;

    public RoundController(RoundService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("rounds", service.findRounds(user));
        return "round/list";
    }

    // ---------- 회차 생성 (주관자) ----------

    @GetMapping("/new")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String newRound(@RequestParam(required = false) Integer year,
                           @RequestParam(required = false) Integer quarter, Model model) {
        model.addAttribute("year", year != null ? year : LocalDate.now().getYear());
        model.addAttribute("quarter", quarter != null ? quarter : RoundService.currentQuarter());
        if (year != null && quarter != null) {
            try {
                AuditRound existing = service.findRoundByQuarter(year, quarter);
                if (existing != null) {
                    model.addAttribute("existing", existing);
                } else {
                    List<RoundCandidate> candidates = service.extractCandidates(year, quarter);
                    model.addAttribute("candidates", candidates);
                    model.addAttribute("previousCount", candidates.stream().filter(c -> c.previous() != null).count());
                }
            } catch (BusinessException e) {
                model.addAttribute("errorMessage", e.getMessage());
            }
        }
        return "round/new";
    }

    @PostMapping
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String create(@ModelAttribute RoundCreateForm form, @AuthenticationPrincipal LoginUser actor,
                         RedirectAttributes ra) {
        try {
            Long roundId = service.create(form, actor);
            ra.addFlashAttribute("message", form.getYear() + "년 " + form.getQuarter() + "분기 회차를 만들었습니다. 담당팀·담당자를 배정하세요.");
            return "redirect:/rounds/" + roundId;
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/rounds/new?year=" + form.getYear() + "&quarter=" + form.getQuarter();
        }
    }

    // ---------- 점검 총괄표 ----------

    @GetMapping("/{roundId}")
    public String detail(@PathVariable Long roundId, @AuthenticationPrincipal LoginUser user, Model model) {
        AuditRound round = service.getRound(roundId, user);
        List<RoundItem> items = service.findItems(roundId);
        model.addAttribute("round", round);
        model.addAttribute("items", items.stream().filter(i -> !i.isExcluded()).toList());
        model.addAttribute("excludedItems", items.stream().filter(RoundItem::isExcluded).toList());
        return "round/detail";
    }

    // ---------- 배정 편집 (주관자) ----------

    @GetMapping("/{roundId}/assign")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String assignForm(@PathVariable Long roundId, @AuthenticationPrincipal LoginUser user, Model model,
                             RedirectAttributes ra) {
        AuditRound round = service.getRound(roundId, user);
        if (!round.getStatus().isEditable()) {
            ra.addFlashAttribute("errorMessage", "감사부서에 제출된 회차는 배정을 바꿀 수 없습니다.");
            return "redirect:/rounds/" + roundId;
        }
        List<RoundItem> items = service.findItems(roundId);
        AssignmentForm form = new AssignmentForm();
        items.forEach(i -> form.getRows().add(AssignmentForm.Row.of(i)));
        return assignView(model, round, items, form);
    }

    @PostMapping("/{roundId}/assign")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String saveAssignment(@PathVariable Long roundId, @ModelAttribute("form") AssignmentForm form,
                                 @AuthenticationPrincipal LoginUser actor, Model model, RedirectAttributes ra) {
        try {
            int changed = service.saveAssignment(roundId, form, actor);
            ra.addFlashAttribute("message", changed == 0 ? "바뀐 내용이 없습니다." : changed + "개 항목의 배정을 저장했습니다.");
            return "redirect:/rounds/" + roundId;
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            AuditRound round = service.getRound(roundId, actor);
            return assignView(model, round, service.findItems(roundId), form);
        }
    }

    private String assignView(Model model, AuditRound round, List<RoundItem> items, AssignmentForm form) {
        model.addAttribute("round", round);
        model.addAttribute("items", items);
        model.addAttribute("form", form);
        model.addAttribute("teams", service.findActiveTeams());
        model.addAttribute("inspectors", service.findInspectors());
        return "round/assign";
    }
}
