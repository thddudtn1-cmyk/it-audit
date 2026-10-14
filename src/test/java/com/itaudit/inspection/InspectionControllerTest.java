package com.itaudit.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.evidence.Evidence;
import com.itaudit.evidence.EvidenceMapper;
import com.itaudit.evidence.EvidenceOwnerType;
import com.itaudit.round.RoundCreateForm;
import com.itaudit.round.RoundItem;
import com.itaudit.round.RoundMapper;
import com.itaudit.round.RoundService;
import com.itaudit.round.SubmitStatus;
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InspectionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired RoundService roundService;
    @Autowired RoundMapper roundMapper;
    @Autowired EvidenceMapper evidenceMapper;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.data-dir}") String dataDir;

    LoginUser lead;
    LoginUser kim;
    LoginUser lee;
    Long roundId;
    Long item23;
    Long item24;

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("i-lead", Role.AUDIT_LEAD);
        testUsers.team("INFRA", "인프라팀");
        kim = testUsers.inspector("i-kim", "INFRA");
        lee = testUsers.inspector("i-lee", "INFRA");
        testChecklist.load(2026, lead);
        roundId = createRound(2026, 3);
        item23 = assign(roundId, 23, kim);
        item24 = assign(roundId, 24, lee);
    }

    private Long createRound(int year, int quarter) {
        RoundCreateForm form = new RoundCreateForm();
        form.setYear(year);
        form.setQuarter(quarter);
        return roundService.create(form, lead);
    }

    private Long assign(Long roundId, int seqNo, LoginUser inspector) {
        RoundItem item = roundMapper.findItems(roundId).stream().filter(i -> i.getSeqNo() == seqNo).findFirst().orElseThrow();
        jdbc.update("UPDATE round_item SET team_code = 'INFRA', assignee_id = ? WHERE round_item_id = ?",
                inspector.getUserId(), item.getRoundItemId());
        return item.getRoundItemId();
    }

    private MockMultipartHttpServletRequestBuilder input(Long itemId, LoginUser u, String action) {
        MockMultipartHttpServletRequestBuilder req = multipart("/inspections/" + itemId);
        req.with(user(u)).with(csrf()).param("action", action);
        return req;
    }

    private static MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("files", name, "application/octet-stream", text.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- 내 할 일 · 열람 권한 ----------

    @Test
    void 내_할_일에는_본인에게_배정된_항목만_보인다() throws Exception {
        String html = mvc.perform(get("/my-tasks").with(user(kim))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("입력 필요 1건").contains("/inspections/" + item23).doesNotContain("/inspections/" + item24);
    }

    @Test
    void 점검_입력_화면은_배정된_담당자만_입력할_수_있다() throws Exception {
        mvc.perform(get("/inspections/" + item23).with(user(kim)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"submit\"")))
                .andExpect(content().string(Matchers.containsString("점검방식")))
                .andExpect(content().string(Matchers.containsString("지난 회차 이력")));
        mvc.perform(get("/inspections/" + item23).with(user(lee))).andExpect(status().isForbidden());

        // 주관자·조회자는 읽기만
        for (LoginUser u : List.of(lead, testUsers.user("i-viewer", Role.VIEWER))) {
            mvc.perform(get("/inspections/" + item23).with(user(u)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"submit\""))));
            mvc.perform(input(item23, u, "save").param("inspectionResult", "NORMAL"))
                    .andExpect(content().string(Matchers.containsString("배정된 점검 담당자만")));
        }
        // 관리자는 점검 데이터를 보지 않음, 감사부서는 제출된 회차만
        mvc.perform(get("/inspections/" + item23).with(user(testUsers.user("i-admin", Role.ADMIN)))).andExpect(status().isForbidden());
        LoginUser dept = testUsers.user("i-dept", Role.AUDIT_DEPT);
        mvc.perform(get("/inspections/" + item23).with(user(dept))).andExpect(status().isForbidden());
        jdbc.update("UPDATE audit_round SET status = 'SUBMITTED' WHERE round_id = ?", roundId);
        mvc.perform(get("/inspections/" + item23).with(user(dept))).andExpect(status().isOk());
    }

    @Test
    void 없는_항목은_안내_화면을_보여준다() throws Exception {
        mvc.perform(get("/inspections/999999").with(user(kim)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString("점검항목을 찾을 수 없습니다")));
    }

    // ---------- 임시저장 · 제출 ----------

    @Test
    void 임시저장하면_입력값과_증적이_저장되고_판정_변경_이력이_남는다() throws Exception {
        mvc.perform(input(item23, kim, "save")
                        .file(file("접근권한_변경대장.xlsx", "xlsx"))
                        .file(file("캡처.png", "png"))
                        .param("inspectionResult", "INSUFFICIENT")
                        .param("inspectionDetail", "인사이동 3건 중 1건 권한 미회수")
                        .param("remark1", "인사팀 통보 지연")
                        .param("actionPlan", "권한 회수 절차 보완")
                        .param("plannedDate", "2026-12-31"))
                .andExpect(redirectedUrl("/inspections/" + item23));

        RoundItem saved = roundMapper.findItem(item23);
        assertThat(saved.getInspectionResult()).isEqualTo(InspectionResult.INSUFFICIENT);
        assertThat(saved.getInspectionDetail()).isEqualTo("인사이동 3건 중 1건 권한 미회수");
        assertThat(saved.getRemark1()).isEqualTo("인사팀 통보 지연");
        assertThat(saved.getPlannedDate()).hasToString("2026-12-31");
        assertThat(saved.getSubmitStatus()).isEqualTo(SubmitStatus.NOT_SUBMITTED);
        assertThat(saved.getEvidenceCount()).isEqualTo(2);

        List<Evidence> files = evidenceMapper.findActive(EvidenceOwnerType.ROUND_ITEM, item23);
        assertThat(files).extracting(Evidence::getOriginalName).containsExactly("접근권한_변경대장.xlsx", "캡처.png");
        assertThat(files.get(0).getStoredPath()).startsWith("files/").doesNotContain("접근권한");
        assertThat(Path.of(dataDir).resolve(files.get(0).getStoredPath())).exists();

        assertThat(activityLogMapper.findByAction(ActivityLogService.INSPECTION_SAVE))
                .anyMatch(l -> l.getDetail().contains("점검결과 없음 → 미흡"));
        assertThat(activityLogMapper.findByAction(ActivityLogService.EVIDENCE_UPLOAD)).hasSize(2);
    }

    @Test
    void 정상이_아닌데_조치내용과_개선_예정일이_없으면_제출되지_않는다() throws Exception {
        mvc.perform(input(item23, kim, "submit")
                        .file(file("a.pdf", "pdf"))
                        .param("inspectionResult", "RISK")
                        .param("inspectionDetail", "위반 확인"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("조치내용을 입력해야 합니다")))
                .andExpect(content().string(Matchers.containsString("개선 예정일을 입력해야 합니다")))
                .andExpect(content().string(Matchers.containsString("선택한 파일은 다시 골라 주세요")));

        RoundItem item = roundMapper.findItem(item23);
        assertThat(item.getInspectionResult()).as("검증 실패 시 아무것도 저장하지 않음").isNull();
        assertThat(item.getEvidenceCount()).isZero();
        assertThat(item.getSubmitStatus()).isEqualTo(SubmitStatus.NOT_SUBMITTED);
    }

    @Test
    void 점검결과와_내용_없이는_제출할_수_없다() throws Exception {
        mvc.perform(input(item23, kim, "submit"))
                .andExpect(content().string(Matchers.containsString("점검결과를 선택하세요")))
                .andExpect(content().string(Matchers.containsString("점검 내용 상세를 입력하세요")));
        // 임시저장은 빈 값도 허용
        mvc.perform(input(item23, kim, "save")).andExpect(redirectedUrl("/inspections/" + item23));
    }

    @Test
    void 제출하면_잠기고_회차_제출률에_반영된다() throws Exception {
        mvc.perform(input(item23, kim, "submit")
                        .param("inspectionResult", "NORMAL")
                        .param("inspectionDetail", "권한 회수 대장 확인, 이상 없음"))
                .andExpect(redirectedUrl("/my-tasks"));

        RoundItem item = roundMapper.findItem(item23);
        assertThat(item.getSubmitStatus()).isEqualTo(SubmitStatus.SUBMITTED);
        assertThat(item.getSubmittedBy()).isEqualTo(kim.getUserId());
        assertThat(item.getSubmittedAt()).isNotNull();
        assertThat(roundMapper.findRound(roundId).getSubmittedCount()).isEqualTo(1);
        assertThat(activityLogMapper.findByAction(ActivityLogService.INSPECTION_SUBMIT))
                .anyMatch(l -> l.getDetail().contains("#23 제출, 점검결과=정상"));

        // 제출 후에는 수정 불가, 화면에는 제출 버튼 없음
        mvc.perform(input(item23, kim, "save").param("inspectionResult", "RISK"))
                .andExpect(content().string(Matchers.containsString("이미 제출한 항목입니다")));
        assertThat(roundMapper.findItem(item23).getInspectionResult()).isEqualTo(InspectionResult.NORMAL);
        mvc.perform(get("/inspections/" + item23).with(user(kim)))
                .andExpect(content().string(Matchers.not(Matchers.containsString("value=\"submit\""))));
        mvc.perform(get("/my-tasks").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("입력 필요 0건")))
                .andExpect(content().string(Matchers.containsString("제출 완료·회신불필요 1건")));
        // 총괄표에 점검결과 표시
        mvc.perform(get("/rounds/" + roundId).with(user(lead)))
                .andExpect(content().string(Matchers.containsString("권한 회수 대장 확인, 이상 없음")));
    }

    @Test
    void 반려되면_다시_수정해_제출할_수_있다() throws Exception {
        mvc.perform(input(item23, kim, "submit").param("inspectionResult", "NORMAL").param("inspectionDetail", "확인"));
        jdbc.update("UPDATE round_item SET submit_status = 'REJECTED' WHERE round_item_id = ?", item23);

        mvc.perform(input(item23, kim, "submit")
                        .param("inspectionResult", "NEEDS_IMPROVEMENT").param("inspectionDetail", "보완")
                        .param("actionPlan", "절차 문서화").param("plannedDate", "2026-11-30"))
                .andExpect(redirectedUrl("/my-tasks"));
        assertThat(roundMapper.findItem(item23).getSubmitStatus()).isEqualTo(SubmitStatus.SUBMITTED);
        assertThat(activityLogMapper.findByAction(ActivityLogService.INSPECTION_SUBMIT))
                .anyMatch(l -> l.getDetail().contains("점검결과 정상 → 개선필요"));
    }

    @Test
    void 감사부서에_제출된_회차는_입력할_수_없다() throws Exception {
        jdbc.update("UPDATE audit_round SET status = 'SUBMITTED' WHERE round_id = ?", roundId);
        mvc.perform(input(item23, kim, "save").param("inspectionResult", "NORMAL"))
                .andExpect(content().string(Matchers.containsString("감사부서에 제출된 회차라 수정할 수 없습니다")));
    }

    @Test
    void 허용하지_않는_확장자는_거부한다() throws Exception {
        mvc.perform(input(item23, kim, "save")
                        .file(file("악성.exe", "MZ"))
                        .param("inspectionResult", "NORMAL"))
                .andExpect(content().string(Matchers.containsString("올릴 수 없는 파일 형식입니다: 악성.exe")));
        assertThat(roundMapper.findItem(item23).getInspectionResult()).isNull();
        assertThat(evidenceMapper.findActive(EvidenceOwnerType.ROUND_ITEM, item23)).isEmpty();
    }

    // ---------- 증적 내려받기 · 삭제 ----------

    @Test
    void 증적은_열람_권한이_있는_사람만_내려받는다() throws Exception {
        mvc.perform(input(item23, kim, "save").file(file("점검 결과.txt", "점검 내용 원본")));
        Long evId = evidenceMapper.findActive(EvidenceOwnerType.ROUND_ITEM, item23).get(0).getEvidenceId();

        byte[] body = mvc.perform(get("/evidence/" + evId).with(user(kim)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("UTF-8''")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("점검 내용 원본");

        mvc.perform(get("/evidence/" + evId).with(user(lead))).andExpect(status().isOk());
        mvc.perform(get("/evidence/" + evId).with(user(lee))).andExpect(status().isForbidden());
    }

    @Test
    void 증적_삭제는_논리_삭제이고_파일과_이력은_남는다() throws Exception {
        mvc.perform(input(item23, kim, "save").file(file("old.pdf", "v1")));
        Evidence ev = evidenceMapper.findActive(EvidenceOwnerType.ROUND_ITEM, item23).get(0);

        // 다른 담당자는 삭제 불가
        mvc.perform(post("/evidence/" + ev.getEvidenceId() + "/delete").param("returnUrl", "/my-tasks")
                        .with(user(lee)).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/evidence/" + ev.getEvidenceId() + "/delete").param("returnUrl", "/inspections/" + item23)
                        .with(user(kim)).with(csrf()))
                .andExpect(redirectedUrl("/inspections/" + item23));

        Evidence deleted = evidenceMapper.findById(ev.getEvidenceId());
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedBy()).isEqualTo(kim.getUserId());
        assertThat(Files.exists(Path.of(dataDir).resolve(deleted.getStoredPath()))).as("물리 파일 유지").isTrue();
        assertThat(evidenceMapper.findActive(EvidenceOwnerType.ROUND_ITEM, item23)).isEmpty();
        assertThat(activityLogMapper.findByAction(ActivityLogService.EVIDENCE_DELETE))
                .anyMatch(l -> l.getDetail().contains("old.pdf"));
        mvc.perform(get("/evidence/" + ev.getEvidenceId()).with(user(kim))).andExpect(status().isBadRequest());
    }

    @Test
    void 제출한_항목의_증적은_삭제할_수_없다() throws Exception {
        mvc.perform(input(item23, kim, "submit").file(file("final.pdf", "v1"))
                .param("inspectionResult", "NORMAL").param("inspectionDetail", "확인"));
        Evidence ev = evidenceMapper.findActive(EvidenceOwnerType.ROUND_ITEM, item23).get(0);

        mvc.perform(post("/evidence/" + ev.getEvidenceId() + "/delete").param("returnUrl", "/inspections/" + item23)
                .with(user(kim)).with(csrf()));
        assertThat(evidenceMapper.findById(ev.getEvidenceId()).isDeleted()).isFalse();
    }

    // ---------- 지난 회차 이력 ----------

    @Test
    void 같은_항목의_지난_회차_결과를_보여준다() throws Exception {
        // 2026년 3분기에 #23 제출 → 2027년 3분기 회차에서 이력으로 보임
        mvc.perform(input(item23, kim, "submit").param("inspectionResult", "NORMAL").param("inspectionDetail", "작년 점검 내용"));
        jdbc.update("INSERT INTO checklist_item (apply_year, seq_no, audit_area, audit_item, item_detail, audit_period, "
                + "regulation, use_yn, created_at, updated_at) SELECT 2027, seq_no, audit_area, audit_item, item_detail, "
                + "audit_period, regulation, use_yn, created_at, updated_at FROM checklist_item WHERE apply_year = 2026");
        Long next = createRound(2027, 3);
        Long next23 = roundMapper.findItems(next).stream().filter(i -> i.getSeqNo() == 23).findFirst().orElseThrow().getRoundItemId();
        assertThat(roundMapper.findItem(next23).getAssigneeId()).as("지난 회차 배정 복사").isEqualTo(kim.getUserId());

        mvc.perform(get("/inspections/" + next23).with(user(kim)))
                .andExpect(content().string(Matchers.containsString("2026년 3분기")))
                .andExpect(content().string(Matchers.containsString("작년 점검 내용")));
    }
}
