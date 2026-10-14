package com.itaudit.auditlog;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.itaudit.common.ActivityLog;
import com.itaudit.common.ActivityLogMapper;

/**
 * 감사 로그 (F-11): 점검결과 변경·제출·미흡 확정·종결·파일 업로드·삭제 등 기록을 사용자·시각과 함께 조회한다.
 * 읽기 전용이며 수정·삭제 기능은 없다. 메뉴 권한은 Menu.AUDIT_LOG (관리자·주관자).
 */
@Controller
public class AuditLogController {

    static final int PAGE_SIZE = 50;

    private final ActivityLogMapper mapper;

    public AuditLogController(ActivityLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/audit-log")
    public String list(@RequestParam(required = false) String action,
                       @RequestParam(required = false) String loginId,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        String a = StringUtils.hasText(action) ? action : null;
        String u = StringUtils.hasText(loginId) ? loginId.trim() : null;
        String k = StringUtils.hasText(keyword) ? "%" + keyword.trim().replace("%", "\\%").replace("_", "\\_") + "%" : null;
        var fromAt = from == null ? null : from.atStartOfDay();
        var toAt = to == null ? null : to.plusDays(1).atStartOfDay();

        int total = mapper.count(a, u, k, fromAt, toAt);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(1, page), pages);
        List<ActivityLog> logs = mapper.search(a, u, k, fromAt, toAt, (current - 1) * PAGE_SIZE, PAGE_SIZE);

        model.addAttribute("logs", logs);
        model.addAttribute("actions", ActionLabels.all());
        model.addAttribute("total", total);
        model.addAttribute("page", current);
        model.addAttribute("pages", pages);
        model.addAttribute("action", action);
        model.addAttribute("loginId", loginId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "auditlog/list";
    }

    /** 템플릿에서 활동 코드를 한글 이름으로 */
    @org.springframework.web.bind.annotation.ModelAttribute("labelOf")
    public java.util.function.Function<String, String> labelOf() {
        return ActionLabels::label;
    }
}
