package com.itaudit.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.itaudit.auth.AppUser;
import com.itaudit.auth.AppUserMapper;
import com.itaudit.auth.LoginUser;
import com.itaudit.auth.PasswordPolicy;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;

/** 관리 화면: 사용자·담당팀 코드. 삭제 대신 사용여부(N)로 중지한다. */
@Service
public class AdminService {

    private final AppUserMapper userMapper;
    private final TeamMapper teamMapper;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLog;

    public AdminService(AppUserMapper userMapper, TeamMapper teamMapper,
                        PasswordEncoder passwordEncoder, ActivityLogService activityLog) {
        this.userMapper = userMapper;
        this.teamMapper = teamMapper;
        this.passwordEncoder = passwordEncoder;
        this.activityLog = activityLog;
    }

    // ---------- 사용자 ----------

    public List<AppUser> findUsers() {
        return userMapper.findAll();
    }

    public AppUser getUser(Long userId) {
        AppUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("사용자를 찾을 수 없습니다.");
        }
        return user;
    }

    @Transactional
    public Long createUser(UserForm form) {
        if (userMapper.findByLoginId(form.getLoginId()) != null) {
            throw new BusinessException("이미 사용 중인 아이디입니다.");
        }
        PasswordPolicy.validate(form.getInitialPassword());
        String teamCode = checkTeam(form.getTeamCode(), form.getRoleCode());

        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setLoginId(form.getLoginId());
        user.setUserName(form.getUserName().trim());
        user.setTeamCode(teamCode);
        user.setRoleCode(form.getRoleCode());
        user.setPasswordHash(passwordEncoder.encode(form.getInitialPassword()));
        // 관리자가 정한 초기 비밀번호는 본인이 첫 로그인 때 바꾼다
        user.setPwChangeRequired("Y");
        user.setUseYn(form.isActive() ? "Y" : "N");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        activityLog.log(ActivityLogService.USER_CREATE, "USER", user.getUserId(),
                "아이디=" + user.getLoginId() + ", 역할=" + user.getRoleCode().getLabel());
        return user.getUserId();
    }

    @Transactional
    public void updateUser(Long userId, UserForm form, LoginUser actor) {
        AppUser user = getUser(userId);
        if (Objects.equals(userId, actor.getUserId())) {
            if (!form.isActive() || form.getRoleCode() != Role.ADMIN) {
                throw new BusinessException("본인 계정의 관리자 역할이나 사용여부는 바꿀 수 없습니다.");
            }
        }
        String before = describe(user);
        user.setUserName(form.getUserName().trim());
        user.setTeamCode(checkTeam(form.getTeamCode(), form.getRoleCode()));
        user.setRoleCode(form.getRoleCode());
        user.setUseYn(form.isActive() ? "Y" : "N");
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);
        activityLog.log(ActivityLogService.USER_UPDATE, "USER", userId, before + " → " + describe(user));
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        AppUser user = getUser(userId);
        PasswordPolicy.validate(newPassword);
        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(newPassword), "Y", LocalDateTime.now());
        activityLog.log(ActivityLogService.USER_PASSWORD_RESET, "USER", userId, null);
    }

    /** 점검 담당자는 담당팀이 필수 (총괄표의 담당팀·담당자 배정 대상) */
    private String checkTeam(String teamCode, Role role) {
        if (!StringUtils.hasText(teamCode)) {
            if (role == Role.INSPECTOR) {
                throw new BusinessException("점검 담당자는 담당팀을 지정해야 합니다.");
            }
            return null;
        }
        Team team = teamMapper.findByCode(teamCode);
        if (team == null || !team.isActive()) {
            throw new BusinessException("사용할 수 없는 담당팀입니다.");
        }
        return teamCode;
    }

    private static String describe(AppUser u) {
        return "[" + u.getUserName() + ", 팀=" + u.getTeamCode() + ", 역할=" + u.getRoleCode().name()
                + ", 사용=" + u.getUseYn() + "]";
    }

    // ---------- 담당팀 코드 ----------

    public List<Team> findTeams() {
        return teamMapper.findAll();
    }

    public List<Team> findActiveTeams() {
        return teamMapper.findActive();
    }

    @Transactional
    public void createTeam(TeamForm form) {
        if (teamMapper.findByCode(form.getTeamCode()) != null) {
            throw new BusinessException("이미 있는 팀 코드입니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        Team team = new Team();
        team.setTeamCode(form.getTeamCode());
        team.setTeamName(form.getTeamName().trim());
        team.setSortOrder(form.getSortOrder() == null ? 0 : form.getSortOrder());
        team.setUseYn(form.isActive() ? "Y" : "N");
        team.setCreatedAt(now);
        team.setUpdatedAt(now);
        teamMapper.insert(team);
        activityLog.log(ActivityLogService.TEAM_CREATE, "TEAM", team.getTeamCode(), team.getTeamName());
    }

    @Transactional
    public void updateTeam(String teamCode, TeamForm form) {
        Team team = teamMapper.findByCode(teamCode);
        if (team == null) {
            throw new BusinessException("담당팀을 찾을 수 없습니다.");
        }
        String before = team.getTeamName() + "/" + team.getSortOrder() + "/" + team.getUseYn();
        team.setTeamName(form.getTeamName().trim());
        team.setSortOrder(form.getSortOrder() == null ? 0 : form.getSortOrder());
        team.setUseYn(form.isActive() ? "Y" : "N");
        team.setUpdatedAt(LocalDateTime.now());
        teamMapper.update(team);
        activityLog.log(ActivityLogService.TEAM_UPDATE, "TEAM", teamCode,
                before + " → " + team.getTeamName() + "/" + team.getSortOrder() + "/" + team.getUseYn());
    }
}
