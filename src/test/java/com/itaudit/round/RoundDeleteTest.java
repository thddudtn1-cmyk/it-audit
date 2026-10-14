package com.itaudit.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.checklist.ChecklistMapper;
import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.evidence.EvidenceService;
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestRounds;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoundDeleteTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired TestRounds testRounds;
    @Autowired RoundMapper roundMapper;
    @Autowired ChecklistMapper checklistMapper;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired EvidenceService evidenceService;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.data-dir}") String dataDir;

    LoginUser lead;
    LoginUser dept;
    LoginUser kim;

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("rd-lead", Role.AUDIT_LEAD);
        dept = testUsers.user("rd-dept", Role.AUDIT_DEPT);
        testUsers.team("INFRA", "인프라팀");
        kim = testUsers.inspector("rd-kim", "INFRA");
        testChecklist.load(2026, lead);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder delete(LoginUser u, Long... ids) {
        var req = post("/rounds/delete").with(user(u)).with(csrf());
        for (Long id : ids) {
            req.param("roundIds", String.valueOf(id));
        }
        return req;
    }

    @Test
    void 회차와_그_안의_입력_증적_적정성점검기록_코멘트를_모두_지운다() throws Exception {
        Long roundId = testRounds.create(2026, 3, lead);
        Long other = testRounds.create(2026, 2, lead);
        Long item23 = testRounds.confirmItem(roundId, 23, kim, "INFRA", "INSUFFICIENT", "미회수", "보완", "2026-12-31");
        // 증적을 올리려면 입력 가능한 상태(미제출)여야 한다
        jdbc.update("UPDATE round_item SET submit_status = 'NOT_SUBMITTED' WHERE round_item_id = ?", item23);
        mvc.perform(multipart("/inspections/" + item23)
                        .file(new MockMultipartFile("files", "증적.pdf", null, "pdf".getBytes(StandardCharsets.UTF_8)))
                        .with(user(kim)).with(csrf()).param("action", "save").param("inspectionResult", "INSUFFICIENT")
                        .param("inspectionDetail", "미회수"))
                .andExpect(redirectedUrl("/inspections/" + item23));
        mvc.perform(post("/comments").with(user(lead)).with(csrf()).param("targetType", "ROUND_ITEM")
                .param("targetId", String.valueOf(item23)).param("body", "보완 부탁"));
        jdbc.update("UPDATE round_item SET submit_status = 'CONFIRMED' WHERE round_item_id = ?", item23);
        testRounds.submitToDept(roundId);
        Long requestId = testRounds.confirmFinding(item23, dept.getUserId(), "INSUFFICIENT", "미삭제", null);
        jdbc.update("INSERT INTO evidence (owner_type, owner_id, original_name, stored_path, file_size, uploaded_by, uploaded_at) "
                + "VALUES ('AUDIT_REQUEST', ?, '요청자료.xlsx', 'files/x/req.xlsx', 4, ?, CURRENT_TIMESTAMP)", requestId, kim.getUserId());
        assertThat(count("SELECT COUNT(*) FROM evidence")).isEqualTo(2);

        mvc.perform(delete(lead, roundId))
                .andExpect(redirectedUrl("/rounds"))
                .andExpect(flash().attribute("message", Matchers.containsString("회차 1건")));

        assertThat(roundMapper.findRound(roundId)).isNull();
        assertThat(roundMapper.findRound(other)).as("다른 회차는 그대로").isNotNull();
        assertThat(count("SELECT COUNT(*) FROM round_item WHERE round_id = ?", roundId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM round_item WHERE round_id = ?", other)).isPositive();
        assertThat(count("SELECT COUNT(*) FROM audit_request")).isZero();
        assertThat(count("SELECT COUNT(*) FROM evidence")).isZero();
        assertThat(count("SELECT COUNT(*) FROM comment_note")).isZero();
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_DELETE)).hasSize(1)
                .allMatch(l -> l.getDetail().contains("2026년 3분기 삭제 (상태 감사부서 제출)")
                        && l.getDetail().contains("점검 입력 1건") && l.getDetail().contains("적정성 점검 기록 1건")
                        && l.getDetail().contains("증적 파일 2개") && l.getDetail().contains("코멘트 1건")
                        && l.getDetail().contains("자동 등록 개선사항 0건"));
        // 삭제 후 같은 분기 회차를 다시 만들 수 있다
        assertThat(testRounds.create(2026, 3, lead)).isNotNull();
    }

    @Test
    void 삭제한_회차에_쓰인_체크리스트_항목은_다시_삭제할_수_있다() throws Exception {
        Long roundId = testRounds.create(2026, 3, lead);
        Long item23 = checklistMapper.findBySeqNo(2026, 23).getItemId();
        assertThat(checklistMapper.findById(item23).isUsedInRounds()).isTrue();
        mvc.perform(delete(lead, roundId));
        assertThat(checklistMapper.findById(item23).isUsedInRounds()).isFalse();
    }

    @Test
    void 직접_등록한_개선사항이_연결된_회차는_삭제할_수_없고_함께_선택한_회차도_지우지_않는다() throws Exception {
        Long linked = testRounds.create(2026, 3, lead);
        Long free = testRounds.create(2026, 2, lead);
        jdbc.update("INSERT INTO improvement (kind, round_id, team_code, assignee_id, inspection_result, detail, action_result, "
                + "closed_yn, created_by, created_at, updated_at) VALUES ('IMPROVEMENT', ?, 'INFRA', ?, 'RISK', '내용', 'PLANNED', 'N', ?, "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", linked, kim.getUserId(), lead.getUserId());

        mvc.perform(delete(lead, free, linked))
                .andExpect(flash().attribute("errorMessage", "조치가 진행·종결되었거나 직접 등록한 개선사항·권고사항이 연결된 회차는 삭제할 수 없습니다: 2026년 3분기"));
        assertThat(roundMapper.findRound(linked)).isNotNull();
        assertThat(roundMapper.findRound(free)).isNotNull();

        // 목록에서는 체크박스가 비활성
        String html = mvc.perform(get("/rounds").with(user(lead))).andReturn().getResponse().getContentAsString();
        assertThat(html).containsPattern("name=\"roundIds\" value=\"" + linked + "\"[^>]*disabled")
                .doesNotContainPattern("name=\"roundIds\" value=\"" + free + "\"[^>]*disabled")
                .contains("직접 등록한 개선사항·권고사항 1건이 연결되어 삭제할 수 없습니다");
    }

    @Test
    void 선택이_없거나_없는_회차면_거부한다() throws Exception {
        Long roundId = testRounds.create(2026, 3, lead);
        mvc.perform(delete(lead)).andExpect(flash().attribute("errorMessage", "삭제할 회차를 선택하세요."));
        mvc.perform(delete(lead, 999999L))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("선택한 회차를 찾을 수 없습니다")));
        assertThat(roundMapper.findRound(roundId)).isNotNull();
    }

    @Test
    void 삭제는_주관자만_하고_다른_역할_목록에는_체크박스가_없다() throws Exception {
        Long roundId = testRounds.create(2026, 3, lead);
        testRounds.submitToDept(roundId);
        for (LoginUser u : List.of(dept, kim, testUsers.user("rd-viewer", Role.VIEWER), testUsers.user("rd-admin", Role.ADMIN))) {
            mvc.perform(delete(u, roundId)).andExpect(status().isForbidden());
        }
        assertThat(roundMapper.findRound(roundId)).isNotNull();
        for (LoginUser u : List.of(dept, testUsers.user("rd-viewer2", Role.VIEWER))) {
            mvc.perform(get("/rounds").with(user(u))).andExpect(status().isOk())
                    .andExpect(content().string(Matchers.not(Matchers.containsString("name=\"roundIds\""))))
                    .andExpect(content().string(Matchers.not(Matchers.containsString("선택 삭제"))));
        }
        mvc.perform(get("/rounds").with(user(lead))).andExpect(content().string(Matchers.containsString("선택 삭제")));
    }

    @Test
    void 저장된_증적_파일을_물리_삭제하고_data_밖_경로는_건드리지_않는다() throws Exception {
        Path inside = Path.of(dataDir).toAbsolutePath().resolve("files/test-purge/a.txt");
        Files.createDirectories(inside.getParent());
        Files.writeString(inside, "x");
        Path outside = Files.createTempFile("outside", ".txt");

        int deleted = evidenceService.deleteStoredFiles(List.of("files/test-purge/a.txt", "files/test-purge/none.txt",
                "../../../" + outside.toAbsolutePath()));

        assertThat(deleted).isEqualTo(1);
        assertThat(inside).doesNotExist();
        assertThat(outside).exists();
        Files.deleteIfExists(outside);
    }
}
