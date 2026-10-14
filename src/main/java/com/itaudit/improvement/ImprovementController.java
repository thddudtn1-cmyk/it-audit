package com.itaudit.improvement;

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
import com.itaudit.comment.CommentService;
import com.itaudit.comment.CommentTarget;
import com.itaudit.common.BusinessException;
import com.itaudit.evidence.EvidenceOwnerType;
import com.itaudit.evidence.EvidenceService;
import com.itaudit.inspection.InspectionResult;

/** 개선사항 관리대장 (⑤-1) · 권고사항 (⑤-2) */
@Controller
@RequestMapping("/improvements")
public class ImprovementController {

    private static final List<InspectionResult> FINDING_RESULTS =
            List.of(InspectionResult.NEEDS_IMPROVEMENT, InspectionResult.INSUFFICIENT, InspectionResult.RISK);

    private final ImprovementService service;
    private final EvidenceService evidenceService;
    private final CommentService commentService;

    public ImprovementController(ImprovementService service, EvidenceService evidenceService,
                                 CommentService commentService) {
        this.service = service;
        this.evidenceService = evidenceService;
        this.commentService = commentService;
    }

    @GetMapping
    public String list(@ModelAttribute("filter") ImprovementFilter filter, @AuthenticationPrincipal LoginUser user,
                       Model model) {
        model.addAttribute("items", service.findList(filter, user));
        model.addAttribute("actionResults", ActionResult.values());
        model.addAttribute("rounds", service.findRoundsForRecommendation());
        return "improvement/list";
    }

    // ---------- 개선사항 등록 (주관자) ----------

    @GetMapping("/findings")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String findings(Model model) {
        model.addAttribute("findings", service.findOpenFindings());
        return "improvement/findings";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String newImprovement(@RequestParam(name = "roundItemIds", required = false) List<Long> roundItemIds,
                                 Model model, RedirectAttributes ra) {
        try {
            ImprovementForm form = service.prepareFromFindings(roundItemIds);
            return createView(model, form);
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/improvements/findings";
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String createImprovement(@ModelAttribute("form") ImprovementForm form, @AuthenticationPrincipal LoginUser actor,
                                    Model model, RedirectAttributes ra) {
        try {
            Long id = service.createImprovement(form, actor);
            ra.addFlashAttribute("message", "개선사항을 등록했습니다. 담당자 '내 할 일'에 표시됩니다.");
            return "redirect:/improvements/" + id;
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            form.setKind(ImprovementKind.IMPROVEMENT);
            return createView(model, form);
        }
    }

    // ---------- 권고사항 등록 (감사부서·주관자) ----------

    @GetMapping("/recommendations/new")
    @PreAuthorize("hasAnyRole('AUDIT_DEPT','AUDIT_LEAD')")
    public String newRecommendation(@RequestParam(required = false) Long roundId, Model model) {
        ImprovementForm form = new ImprovementForm();
        form.setKind(ImprovementKind.RECOMMENDATION);
        form.setRoundId(roundId);
        return createView(model, form);
    }

    @PostMapping("/recommendations")
    @PreAuthorize("hasAnyRole('AUDIT_DEPT','AUDIT_LEAD')")
    public String createRecommendation(@ModelAttribute("form") ImprovementForm form,
                                       @AuthenticationPrincipal LoginUser actor, Model model, RedirectAttributes ra) {
        try {
            Long id = service.createRecommendation(form, actor);
            ra.addFlashAttribute("message", "권고사항을 등록했습니다.");
            return "redirect:/improvements/" + id;
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            form.setKind(ImprovementKind.RECOMMENDATION);
            return createView(model, form);
        }
    }

    private String createView(Model model, ImprovementForm form) {
        model.addAttribute("form", form);
        model.addAttribute("teams", service.findActiveTeams());
        model.addAttribute("inspectors", service.findInspectors());
        model.addAttribute("findingResults", FINDING_RESULTS);
        if (form.getKind() == ImprovementKind.IMPROVEMENT) {
            model.addAttribute("findings", service.loadFindings(form.getRoundItemIds()));
        } else {
            model.addAttribute("rounds", service.findRoundsForRecommendation());
            if (form.getRoundId() != null) {
                model.addAttribute("roundItems", service.findRoundItems(form.getRoundId()));
            }
        }
        return "improvement/new";
    }

    // ---------- 상세·수정·완료 검토(최종확정·반려) ----------

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal LoginUser user, Model model) {
        Improvement m = service.get(id, user);
        return detailView(model, m, user, ImprovementForm.of(m));
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") ImprovementForm form,
                         @RequestParam(name = "files", required = false) List<MultipartFile> files,
                         @AuthenticationPrincipal LoginUser user, Model model, RedirectAttributes ra) {
        try {
            service.update(id, form, files, user);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage()
                    + (files != null && files.stream().anyMatch(f -> !f.isEmpty()) ? " (선택한 파일은 다시 골라 주세요)" : ""));
            return detailView(model, service.get(id, user), user, form);
        }
        ra.addFlashAttribute("message", "저장했습니다.");
        return "redirect:/improvements/" + id;
    }

    /** 완료 검토: 상태(최종확정·진행중·예정)를 고르고 코멘트를 적어 처리한다 */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String review(@PathVariable Long id, @RequestParam(required = false) ImprovementService.ReviewOutcome status,
                         @RequestParam(required = false) String comment,
                         @AuthenticationPrincipal LoginUser user, RedirectAttributes ra) {
        try {
            ImprovementService.ReviewOutcome outcome = service.review(id, status, comment, user);
            ra.addFlashAttribute("message", outcome == ImprovementService.ReviewOutcome.CLOSED
                    ? "최종확정했습니다."
                    : "이전 단계(" + ActionResult.valueOf(outcome.name()).getLabel() + ")로 되돌렸습니다. 담당자가 사유를 확인하고 다시 조치합니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/improvements/" + id;
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String close(@PathVariable Long id, @RequestParam(required = false) String comment,
                        @AuthenticationPrincipal LoginUser user, RedirectAttributes ra) {
        try {
            service.close(id, comment, user);
            ra.addFlashAttribute("message", "최종확정했습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/improvements/" + id;
    }

    /** 완료로 올라온 건을 이전 단계(진행중·예정)로 되돌린다. 사유 필수 */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String sendBack(@PathVariable Long id, @RequestParam(required = false) ActionResult target,
                           @RequestParam(required = false) String reason,
                           @AuthenticationPrincipal LoginUser user, RedirectAttributes ra) {
        try {
            service.sendBack(id, target, reason, user);
            ra.addFlashAttribute("message", "이전 단계(" + target.getLabel() + ")로 되돌렸습니다. 담당자가 사유를 확인하고 다시 조치합니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/improvements/" + id;
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('AUDIT_LEAD')")
    public String reopen(@PathVariable Long id, @RequestParam(required = false) String reason,
                         @AuthenticationPrincipal LoginUser user, RedirectAttributes ra) {
        try {
            service.reopen(id, reason, user);
            ra.addFlashAttribute("message", "최종확정을 취소했습니다. 완료 검토 대기 상태로 돌아갑니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/improvements/" + id;
    }

    private String detailView(Model model, Improvement m, LoginUser user, ImprovementForm form) {
        model.addAttribute("m", m);
        model.addAttribute("form", form);
        model.addAttribute("perm", service.permissions(m, user));
        model.addAttribute("teams", service.findActiveTeams());
        model.addAttribute("inspectors", service.findInspectors());
        model.addAttribute("findingResults", FINDING_RESULTS);
        model.addAttribute("actionResults", ActionResult.values());
        model.addAttribute("evidences", evidenceService.findActive(EvidenceOwnerType.IMPROVEMENT, m.getImprovementId()));
        model.addAttribute("comments", commentService.find(CommentTarget.IMPROVEMENT, m.getImprovementId(), user));
        model.addAttribute("canComment", commentService.canWrite(user));
        return "improvement/detail";
    }
}
