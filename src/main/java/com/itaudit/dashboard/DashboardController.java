package com.itaudit.dashboard;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.itaudit.auth.LoginUser;
import com.itaudit.improvement.Improvement;
import com.itaudit.round.AuditRound;
import com.itaudit.round.RoundStatus;

/** 현황 대시보드 (주관자·감사부서·조회자) */
@Controller
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) Long roundId, @AuthenticationPrincipal LoginUser user,
                            Model model) {
        List<AuditRound> rounds = service.findRounds(user);
        AuditRound round = service.pickRound(rounds, roundId);
        List<Improvement> open = service.openImprovements();
        List<Improvement> overdue = open.stream().filter(Improvement::isOverdue).toList();
        List<Improvement> dueSoon = open.stream().filter(Improvement::isDueSoon).toList();

        model.addAttribute("rounds", rounds);
        model.addAttribute("round", round);
        model.addAttribute("activeRounds", rounds.stream().filter(r -> r.getStatus() != RoundStatus.DONE).toList());
        model.addAttribute("recentRounds", rounds.stream().limit(8).toList());
        if (round != null) {
            List<CountRow> assignees = service.assigneeStats(round.getRoundId());
            List<CountRow> results = service.resultStats(round.getRoundId());
            model.addAttribute("assignees", assignees);
            model.addAttribute("results", results);
            model.addAttribute("resultMax", DashboardService.max(results));
        }
        List<CountRow> actions = service.actionStats();
        model.addAttribute("actions", actions);
        model.addAttribute("actionMax", DashboardService.max(actions));
        model.addAttribute("openCount", open.size());
        model.addAttribute("overdue", overdue);
        model.addAttribute("dueSoon", dueSoon);
        return "dashboard/index";
    }
}
