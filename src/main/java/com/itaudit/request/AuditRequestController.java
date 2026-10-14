package com.itaudit.request;

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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.BusinessException;
import com.itaudit.evidence.EvidenceOwnerType;
import com.itaudit.evidence.EvidenceService;
import com.itaudit.inspection.InspectionResult;
import com.itaudit.round.AuditRound;

/**
 * 적정성 점검 (④): 감사부서가 제출된 분기 점검 내역을 항목별로 검토·판정하고 보완자료를 요청하며,
 * 담당자는 요청받은 보완자료를 제출한다. (URL 은 기존 /requests 를 유지한다.)
 */
@Controller
@RequestMapping("/requests")
public class AuditRequestController {

    private final AuditRequestService service;
    private final EvidenceService evidenceService;

    public AuditRequestController(AuditRequestService service, EvidenceService evidenceService) {
        this.service = service;
        this.evidenceService = evidenceService;
    }

    /** 감사부서·주관자: 회차의 점검 항목별 적정성 점검 현황 / 점검 담당자: 받은 보완자료 요청 */
    @GetMapping
    public String list(@RequestParam(required = false) Long roundId, @AuthenticationPrincipal LoginUser user, Model model) {
        if (user.getRole() == Role.INSPECTOR) {
            model.addAttribute("requests", service.findMine(user));
            return "request/my-list";
        }
        List<AuditRound> rounds = service.findRounds();
        model.addAttribute("rounds", rounds);
        AuditRound round = null;
        if (roundId != null) {
            round = service.getRound(roundId);
        } else if (!rounds.isEmpty()) {
            // 기본: 적정성 점검이 진행 중인 가장 최근 회차, 없으면 가장 최근 회차
            round = rounds.stream().filter(r -> r.getStatus().name().equals("SUBMITTED")).findFirst().orElse(rounds.get(0));
        }
        model.addAttribute("round", round);
        if (round != null) {
            List<AuditRequestService.Target> targets = service.findTargets(round.getRoundId());
            model.addAttribute("targets", targets);
            model.addAttribute("countNotReviewed", count(targets, ReviewState.NOT_REVIEWED));
            model.addAttribute("countRequested", count(targets, ReviewState.REQUESTED));
            model.addAttribute("countSubmitted", count(targets, ReviewState.SUBMITTED));
            model.addAttribute("countPassed", count(targets, ReviewState.PASSED));
            model.addAttribute("countFinding", count(targets, ReviewState.FINDING));
            model.addAttribute("openCount", targets.stream().filter(t -> !t.state().isDone()).count());
        }
        return "request/list";
    }

    private static long count(List<AuditRequestService.Target> targets, ReviewState state) {
        return targets.stream().filter(t -> t.state() == state).count();
    }

    @PostMapping("/rounds/{roundId}/complete")
    @PreAuthorize("hasRole('AUDIT_DEPT')")
    public String complete(@PathVariable Long roundId, @AuthenticationPrincipal LoginUser actor, RedirectAttributes ra) {
        try {
            service.completeRound(roundId, actor);
            ra.addFlashAttribute("message", "적정성 점검을 완료했습니다. 미흡 확정 건은 주관자가 개선사항 관리대장에 등록합니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/requests?roundId=" + roundId;
    }

    // ---------- 항목 상세 · 판정 · 보완자료 제출 ----------

    @GetMapping("/items/{roundItemId}")
    public String detail(@PathVariable Long roundItemId, @AuthenticationPrincipal LoginUser user, Model model) {
        AuditRequestService.TargetView t = service.getTarget(roundItemId, user);
        return render(model, t, user, t.review() == null ? null : t.review().getRemark(), null);
    }

    /** 보완자료 요청 기록 번호로 들어오는 기존 주소 → 항목 상세 */
    @GetMapping("/{requestId}")
    public String byRequest(@PathVariable Long requestId, @AuthenticationPrincipal LoginUser user) {
        return "redirect:/requests/items/" + service.getForView(requestId, user).getRoundItemId();
    }

    @PostMapping("/items/{roundItemId}/review")
    @PreAuthorize("hasRole('AUDIT_DEPT')")
    public String review(@PathVariable Long roundItemId, @ModelAttribute("reviewForm") RequestReviewForm form,
                         @AuthenticationPrincipal LoginUser user, Model model, RedirectAttributes ra) {
        try {
            service.review(roundItemId, form, user);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            AuditRequestService.TargetView t = service.getTarget(roundItemId, user);
            return render(model, t, user, t.review() == null ? null : t.review().getRemark(), form);
        }
        ra.addFlashAttribute("message", switch (form.getDecision()) {
            case FINDING -> "미흡으로 확정했습니다.";
            case NO_FINDING -> "적정으로 판정했습니다.";
            case SUPPLEMENT -> "담당자에게 보완자료를 요청했습니다.";
        });
        return "redirect:/requests/items/" + roundItemId;
    }

    @PostMapping("/{requestId}/submission")
    public String submit(@PathVariable Long requestId, @RequestParam(required = false) String remark,
                         @RequestParam(name = "files", required = false) List<MultipartFile> files,
                         @RequestParam(defaultValue = "save") String action,
                         @AuthenticationPrincipal LoginUser user, Model model, RedirectAttributes ra) {
        boolean submit = "submit".equals(action);
        Long roundItemId = service.getForView(requestId, user).getRoundItemId();
        try {
            service.saveSubmission(requestId, remark, files, submit, user);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage()
                    + (files != null && files.stream().anyMatch(f -> !f.isEmpty()) ? " (선택한 파일은 다시 골라 주세요)" : ""));
            return render(model, service.getTarget(roundItemId, user), user, remark, null);
        }
        ra.addFlashAttribute("message", submit ? "보완자료를 제출했습니다." : "임시저장했습니다.");
        return "redirect:/requests/items/" + roundItemId;
    }

    private String render(Model model, AuditRequestService.TargetView t, LoginUser user, String remark,
                          RequestReviewForm reviewForm) {
        AuditRequest review = t.review();
        model.addAttribute("item", t.item());
        model.addAttribute("round", t.round());
        model.addAttribute("review", review);
        model.addAttribute("state", t.state());
        model.addAttribute("remark", remark);
        String submitBlock = review == null ? null : service.submitBlockReason(review, user);
        model.addAttribute("canSubmit", review != null && submitBlock == null);
        model.addAttribute("submitBlockReason", submitBlock);
        String reviewBlock = AuditRequestService.reviewBlockReason(t.round(), review);
        boolean isDept = user.getRole() == Role.AUDIT_DEPT;
        model.addAttribute("canReview", isDept && reviewBlock == null);
        model.addAttribute("reviewBlockReason", isDept ? reviewBlock : null);
        if (reviewForm == null) {
            reviewForm = new RequestReviewForm();
            if (review != null) {
                reviewForm.setConfirmedResult(review.getConfirmedResult());
                reviewForm.setFindingDetail(review.getFindingDetail());
                reviewForm.setDeptComment(review.getDeptComment());
            }
            if (reviewForm.getConfirmedResult() == null && t.item().getInspectionResult() != null
                    && t.item().getInspectionResult().requiresAction()) {
                reviewForm.setConfirmedResult(t.item().getInspectionResult());
            }
        }
        model.addAttribute("reviewForm", reviewForm);
        model.addAttribute("findingResults",
                List.of(InspectionResult.NEEDS_IMPROVEMENT, InspectionResult.INSUFFICIENT, InspectionResult.RISK));
        model.addAttribute("evidences", review == null ? List.of()
                : evidenceService.findActive(EvidenceOwnerType.AUDIT_REQUEST, review.getRequestId()));
        model.addAttribute("itemEvidences", evidenceService.findActive(EvidenceOwnerType.ROUND_ITEM, t.item().getRoundItemId()));
        return "request/detail";
    }
}
