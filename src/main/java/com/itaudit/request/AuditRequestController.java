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

/** 사전요청자료 (④): 감사부서 요청·판정, 담당자 제출 */
@Controller
@RequestMapping("/requests")
public class AuditRequestController {

    private final AuditRequestService service;
    private final EvidenceService evidenceService;

    public AuditRequestController(AuditRequestService service, EvidenceService evidenceService) {
        this.service = service;
        this.evidenceService = evidenceService;
    }

    /** 감사부서·주관자: 회차별 ④ 표 / 점검 담당자: 받은 요청 목록 */
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
            round = rounds.get(0);
        }
        model.addAttribute("round", round);
        if (round != null) {
            List<AuditRequest> requests = service.findByRound(round.getRoundId());
            model.addAttribute("requests", requests);
            model.addAttribute("openCount", requests.stream().filter(r -> r.getStatus() != RequestStatus.REVIEWED).count());
            model.addAttribute("findingCount", requests.stream().filter(AuditRequest::isFindingConfirmed).count());
        }
        return "request/list";
    }

    // ---------- 요청 (감사부서) ----------

    @GetMapping("/new")
    @PreAuthorize("hasRole('AUDIT_DEPT')")
    public String newRequest(@RequestParam Long roundId, Model model) {
        model.addAttribute("round", service.getRound(roundId));
        model.addAttribute("candidates", service.findCandidates(roundId));
        return "request/new";
    }

    @PostMapping
    @PreAuthorize("hasRole('AUDIT_DEPT')")
    public String create(@ModelAttribute RequestCreateForm form, @AuthenticationPrincipal LoginUser actor,
                         RedirectAttributes ra) {
        try {
            int count = service.create(form, actor);
            ra.addFlashAttribute("message", count + "건을 요청했습니다. 담당자 '내 할 일'에 표시됩니다.");
            return "redirect:/requests?roundId=" + form.getRoundId();
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/requests/new?roundId=" + form.getRoundId();
        }
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

    // ---------- 상세 · 제출 · 판정 ----------

    @GetMapping("/{requestId}")
    public String detail(@PathVariable Long requestId, @AuthenticationPrincipal LoginUser user, Model model) {
        AuditRequest r = service.getForView(requestId, user);
        return render(model, r, user, r.getRemark(), null);
    }

    @PostMapping("/{requestId}/submission")
    public String submit(@PathVariable Long requestId, @RequestParam(required = false) String remark,
                         @RequestParam(name = "files", required = false) List<MultipartFile> files,
                         @RequestParam(defaultValue = "save") String action,
                         @AuthenticationPrincipal LoginUser user, Model model, RedirectAttributes ra) {
        boolean submit = "submit".equals(action);
        try {
            service.saveSubmission(requestId, remark, files, submit, user);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage()
                    + (files != null && files.stream().anyMatch(f -> !f.isEmpty()) ? " (선택한 파일은 다시 골라 주세요)" : ""));
            return render(model, service.getForView(requestId, user), user, remark, null);
        }
        ra.addFlashAttribute("message", submit ? "자료를 제출했습니다." : "임시저장했습니다.");
        return "redirect:/requests/" + requestId;
    }

    @PostMapping("/{requestId}/review")
    @PreAuthorize("hasRole('AUDIT_DEPT')")
    public String review(@PathVariable Long requestId, @ModelAttribute("reviewForm") RequestReviewForm form,
                         @AuthenticationPrincipal LoginUser user, Model model, RedirectAttributes ra) {
        try {
            service.review(requestId, form, user);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            AuditRequest r = service.getForView(requestId, user);
            return render(model, r, user, r.getRemark(), form);
        }
        ra.addFlashAttribute("message", switch (form.getDecision()) {
            case FINDING -> "미흡으로 확정했습니다.";
            case NO_FINDING -> "이상 없음으로 판정했습니다.";
            case SUPPLEMENT -> "담당자에게 보완을 요청했습니다.";
        });
        return "redirect:/requests/" + requestId;
    }

    private String render(Model model, AuditRequest r, LoginUser user, String remark, RequestReviewForm reviewForm) {
        model.addAttribute("req", r);
        model.addAttribute("remark", remark);
        String submitBlock = service.submitBlockReason(r, user);
        model.addAttribute("canSubmit", submitBlock == null);
        model.addAttribute("submitBlockReason", submitBlock);
        model.addAttribute("canReview", user.getRole() == Role.AUDIT_DEPT && AuditRequestService.reviewBlockReason(r) == null);
        if (reviewForm == null) {
            reviewForm = new RequestReviewForm();
            reviewForm.setConfirmedResult(r.getConfirmedResult() != null ? r.getConfirmedResult()
                    : r.getInspectionResult() != null && r.getInspectionResult().requiresAction() ? r.getInspectionResult() : null);
            reviewForm.setFindingDetail(r.getFindingDetail());
            reviewForm.setDeptComment(r.getDeptComment());
        }
        model.addAttribute("reviewForm", reviewForm);
        model.addAttribute("findingResults", List.of(InspectionResult.NEEDS_IMPROVEMENT, InspectionResult.INSUFFICIENT, InspectionResult.RISK));
        model.addAttribute("evidences", evidenceService.findActive(EvidenceOwnerType.AUDIT_REQUEST, r.getRequestId()));
        model.addAttribute("itemEvidences", evidenceService.findActive(EvidenceOwnerType.ROUND_ITEM, r.getRoundItemId()));
        return "request/detail";
    }
}
