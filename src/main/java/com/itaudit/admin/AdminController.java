package com.itaudit.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.itaudit.auth.AppUser;
import com.itaudit.auth.LoginUser;
import com.itaudit.auth.PasswordPolicy;
import com.itaudit.auth.Role;
import com.itaudit.common.BusinessException;

/** 관리 화면 (관리자): 사용자, 담당팀 코드 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public String index() {
        return "redirect:/admin/users";
    }

    // ---------- 사용자 ----------

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", adminService.findUsers());
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUser(Model model) {
        return userForm(model, new UserForm(), null);
    }

    @PostMapping("/users")
    public String createUser(@Validated @ModelAttribute("form") UserForm form, BindingResult binding,
                             Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            return userForm(model, form, null);
        }
        try {
            adminService.createUser(form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return userForm(model, form, null);
        }
        ra.addFlashAttribute("message", "사용자 '" + form.getLoginId() + "'을(를) 등록했습니다.");
        return "redirect:/admin/users";
    }

    @GetMapping("/users/{userId}/edit")
    public String editUser(@PathVariable Long userId, Model model) {
        AppUser user = adminService.getUser(userId);
        UserForm form = new UserForm();
        form.setLoginId(user.getLoginId());
        form.setUserName(user.getUserName());
        form.setTeamCode(user.getTeamCode());
        form.setRoleCode(user.getRoleCode());
        form.setActive(user.isActive());
        return userForm(model, form, userId);
    }

    @PostMapping("/users/{userId}")
    public String updateUser(@PathVariable Long userId,
                             @Validated @ModelAttribute("form") UserForm form, BindingResult binding,
                             @AuthenticationPrincipal LoginUser actor,
                             Model model, RedirectAttributes ra) {
        // 아이디는 수정 대상이 아니므로 저장된 값으로 고정
        form.setLoginId(adminService.getUser(userId).getLoginId());
        if (binding.hasFieldErrors("userName") || binding.hasFieldErrors("roleCode")) {
            return userForm(model, form, userId);
        }
        try {
            adminService.updateUser(userId, form, actor);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return userForm(model, form, userId);
        }
        ra.addFlashAttribute("message", "사용자 '" + form.getLoginId() + "' 정보를 수정했습니다.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{userId}/reset-password")
    public String resetPassword(@PathVariable Long userId, @RequestParam String newPassword, RedirectAttributes ra) {
        try {
            adminService.resetPassword(userId, newPassword);
            ra.addFlashAttribute("message", "비밀번호를 재설정했습니다. 해당 사용자는 다음 로그인 때 비밀번호를 바꿔야 합니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users/" + userId + "/edit";
    }

    private String userForm(Model model, UserForm form, Long userId) {
        model.addAttribute("form", form);
        model.addAttribute("userId", userId);
        model.addAttribute("roles", Role.values());
        model.addAttribute("teams", adminService.findActiveTeams());
        model.addAttribute("policy", PasswordPolicy.DESCRIPTION);
        return "admin/user-form";
    }

    // ---------- 담당팀 코드 ----------

    @GetMapping("/teams")
    public String teams(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TeamForm());
        }
        model.addAttribute("teams", adminService.findTeams());
        return "admin/teams";
    }

    @PostMapping("/teams")
    public String createTeam(@Validated @ModelAttribute("form") TeamForm form, BindingResult binding,
                             Model model, RedirectAttributes ra) {
        if (!binding.hasErrors()) {
            try {
                adminService.createTeam(form);
                ra.addFlashAttribute("message", "담당팀 '" + form.getTeamName() + "'을(를) 등록했습니다.");
                return "redirect:/admin/teams";
            } catch (BusinessException e) {
                model.addAttribute("errorMessage", e.getMessage());
            }
        }
        return teams(model);
    }

    @PostMapping("/teams/{teamCode}")
    public String updateTeam(@PathVariable String teamCode, @RequestParam String teamName,
                             @RequestParam(defaultValue = "0") Integer sortOrder,
                             @RequestParam(defaultValue = "false") boolean active,
                             RedirectAttributes ra) {
        TeamForm form = new TeamForm();
        form.setTeamCode(teamCode);
        form.setTeamName(teamName);
        form.setSortOrder(sortOrder);
        form.setActive(active);
        try {
            if (teamName == null || teamName.isBlank()) {
                throw new BusinessException("팀 이름을 입력하세요.");
            }
            adminService.updateTeam(teamCode, form);
            ra.addFlashAttribute("message", "담당팀 '" + teamCode + "'을(를) 수정했습니다.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/teams";
    }
}
