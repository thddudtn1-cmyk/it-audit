package com.itaudit.comment;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.itaudit.auth.LoginUser;
import com.itaudit.common.BusinessException;

@Controller
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @PostMapping("/comments")
    public String add(@RequestParam CommentTarget targetType, @RequestParam Long targetId, @RequestParam String body,
                      @AuthenticationPrincipal LoginUser user, RedirectAttributes ra) {
        try {
            service.add(targetType, targetId, body, user);
            ra.addFlashAttribute("message", "코멘트를 남겼습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + backUrl(targetType, targetId);
    }

    @PostMapping("/comments/{commentId}/delete")
    public String delete(@PathVariable Long commentId, @AuthenticationPrincipal LoginUser user, RedirectAttributes ra) {
        try {
            Comment c = service.delete(commentId, user);
            ra.addFlashAttribute("message", "코멘트를 삭제했습니다.");
            return "redirect:" + backUrl(c.getTargetType(), c.getTargetId());
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/";
        }
    }

    /** 돌아갈 곳은 대상으로 정해 사이트 밖 주소를 받지 않는다 */
    private static String backUrl(CommentTarget type, Long targetId) {
        return (type == CommentTarget.ROUND_ITEM ? "/inspections/" : "/improvements/") + targetId + "#comments";
    }
}
