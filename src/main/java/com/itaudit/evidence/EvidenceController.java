package com.itaudit.evidence;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.itaudit.auth.LoginUser;
import com.itaudit.common.BusinessException;

/** 증적 파일 내려받기·삭제 (권한은 소유 대상별 EvidenceAccess 가 판단) */
@Controller
public class EvidenceController {

    private final EvidenceService service;

    public EvidenceController(EvidenceService service) {
        this.service = service;
    }

    @GetMapping("/evidence/{evidenceId}")
    public ResponseEntity<Resource> download(@PathVariable Long evidenceId, @AuthenticationPrincipal LoginUser user) {
        Evidence ev = service.getForDownload(evidenceId, user);
        Resource resource = service.open(ev);
        // 브라우저가 내용을 해석해 실행하지 않도록 항상 첨부파일로 내려준다
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(ev.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ev.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    @PostMapping("/evidence/{evidenceId}/delete")
    public String delete(@PathVariable Long evidenceId, @RequestParam String returnUrl,
                         @AuthenticationPrincipal LoginUser user, RedirectAttributes ra) {
        try {
            Evidence ev = service.delete(evidenceId, user);
            ra.addFlashAttribute("message", "파일 '" + ev.getOriginalName() + "'을(를) 삭제했습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + safeReturnUrl(returnUrl);
    }

    /** 사이트 안 경로로만 돌아간다 (오픈 리다이렉트 방지) */
    static String safeReturnUrl(String url) {
        if (url == null || !url.startsWith("/") || url.startsWith("//") || url.contains("\\")) {
            return "/";
        }
        return url;
    }
}
