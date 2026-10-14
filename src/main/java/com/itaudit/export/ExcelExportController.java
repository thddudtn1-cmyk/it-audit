package com.itaudit.export;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.itaudit.auth.LoginUser;

/** 엑셀 내보내기 (F-09). 각 경로는 해당 메뉴의 URL 접근 규칙도 함께 적용된다. */
@Controller
public class ExcelExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelExportService service;

    public ExcelExportController(ExcelExportService service) {
        this.service = service;
    }

    @GetMapping("/checklist/export")
    public ResponseEntity<byte[]> checklist(@RequestParam int year, @AuthenticationPrincipal LoginUser user) {
        return download(service.checklist(year, user));
    }

    @GetMapping("/rounds/{roundId}/export")
    public ResponseEntity<byte[]> round(@PathVariable Long roundId, @AuthenticationPrincipal LoginUser user) {
        return download(service.roundSheet(roundId, user));
    }

    @GetMapping("/requests/export")
    @PreAuthorize("hasAnyRole('AUDIT_DEPT','AUDIT_LEAD')")
    public ResponseEntity<byte[]> requests(@RequestParam Long roundId, @AuthenticationPrincipal LoginUser user) {
        return download(service.requests(roundId, user));
    }

    /** 점검 담당자는 본인 건만 담긴다 */
    @GetMapping("/improvements/export")
    public ResponseEntity<byte[]> improvements(@AuthenticationPrincipal LoginUser user) {
        return download(service.improvements(user));
    }

    private static ResponseEntity<byte[]> download(ExcelExportService.ExcelFile file) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
