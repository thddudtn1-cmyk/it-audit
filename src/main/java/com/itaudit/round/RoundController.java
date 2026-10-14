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
    private final ReviewService reviewService;
    private final RoundDeleteService deleteService;

    public RoundController(RoundService service, ReviewService reviewService, RoundDeleteService deleteService) {
        this.service = service;
        this.reviewService = reviewService;
        this.deleteService = deleteService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("rounds", service.findRounds(user));
        return "round/list";
    }

    // ---------- 회차 삭제 (주관자) ----------

    @PostMapping("/delete")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String delete(@RequestParam(name = "roundIds", required = false) List<Long> roundIds,
                         @AuthenticationPrincipal LoginUser actor, RedirectAttributes ra) {
        try {
            int count = deleteService.delete(roundIds, actor);
            ra.addFlashAttribute("message", "회차 " + count + "건과 그 안의 점검 입력·증적·적정성 점검 기록을 삭제했습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/rounds";
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
        model.addAttribute("notReadyReason", round.isReadyToSubmit() ? null : ReviewService.notReadyReason(round));
        // 감사부서에 제출하면 점검결과가 정상이 아닌 항목이 개선사항 관리대장에 자동 등록된다
        model.addAttribute("autoImprovementCount", items.stream().filter(ReviewService::isImprovementTarget).count());
        return "round/detail";
    }

    // ---------- 총괄표 검토·감사부서 제출 (주관자, F-05) ----------

    @PostMapping("/{roundId}/items/{itemId}/confirm")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String confirm(@PathVariable Long roundId, @PathVariable Long itemId,
                          @AuthenticationPrincipal LoginUser actor, RedirectAttributes ra) {
        return reviewAction(roundId, itemId, ra, () -> reviewService.confirm(itemId, actor), "확인했습니다.");
    }

    @PostMapping("/{roundId}/items/{itemId}/cancel-confirm")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String cancelConfirm(@PathVariable Long roundId, @PathVariable Long itemId,
                                @AuthenticationPrincipal LoginUser actor, RedirectAttributes ra) {
        return reviewAction(roundId, itemId, ra, () -> reviewService.cancelConfirm(itemId, actor), "확인을 취소했습니다.");
    }

    @PostMapping("/{roundId}/items/{itemId}/reject")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String reject(@PathVariable Long roundId, @PathVariable Long itemId, @RequestParam String reason,
                         @AuthenticationPrincipal LoginUser actor, RedirectAttributes ra) {
        return reviewAction(roundId, itemId, ra, () -> reviewService.reject(itemId, reason, actor),
                "반려했습니다. 담당자가 다시 입력해 제출해야 합니다.");
    }

    @PostMapping("/{roundId}/confirm-all")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String confirmAll(@PathVariable Long roundId, @AuthenticationPrincipal LoginUser actor, RedirectAttributes ra) {
        try {
            int count = reviewService.confirmAll(roundId, actor);
            ra.addFlashAttribute("message", count == 0 ? "확인할 제출 항목이 없습니다." : count + "건을 확인했습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/rounds/" + roundId;
    }

    @PostMapping("/{roundId}/submit")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String submit(@PathVariable Long roundId, @AuthenticationPrincipal LoginUser actor, RedirectAttributes ra) {
        try {
            reviewService.submitToAuditDept(roundId, actor);
            ra.addFlashAttribute("message", "감사부서에 제출했습니다. 이제 회차가 잠겨 배정·점검 입력을 바꿀 수 없습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/rounds/" + roundId;
    }

    private String reviewAction(Long roundId, Long itemId, RedirectAttributes ra, Runnable action, String message) {
        try {
            if (!roundId.equals(service.findItemRoundId(itemId))) {
                throw new BusinessException("이 회차의 항목이 아닙니다.");
            }
            action.run();
            ra.addFlashAttribute("message", message);
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/rounds/" + roundId + "#item-" + itemId;
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
