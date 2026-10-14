package com.itaudit.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestRounds;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExcelExportTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired TestRounds testRounds;
    @Autowired JdbcTemplate jdbc;

    LoginUser lead;
    LoginUser dept;
    LoginUser kim;
    Long roundId;
    Long item23;
    Long item26;

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("x-lead", Role.AUDIT_LEAD);
        dept = testUsers.user("x-dept", Role.AUDIT_DEPT);
        testUsers.team("INFRA", "인프라팀");
        kim = testUsers.inspector("x-kim", "INFRA");
        testChecklist.load(2026, lead);
        roundId = testRounds.create(2026, 3, lead);
        item23 = testRounds.confirmItem(roundId, 23, kim, "INFRA", "INSUFFICIENT", "1건 미회수", "회수 절차 보완", "2026-12-31");
        item26 = testRounds.confirmItem(roundId, 26, kim, "INFRA", "NORMAL", "이상 없음", null, null);
        jdbc.update("UPDATE round_item SET excluded_yn = 'Y', exclude_reason = '그룹 IT감사' WHERE round_id = ? AND round_item_id = ?",
                roundId, testRounds.itemId(roundId, 25));
        jdbc.update("UPDATE round_item SET request_material = '계정삭제 수행 이력', sample_size = 5 WHERE round_item_id = ?", item23);
    }

    private XSSFWorkbook download(String url, LoginUser u, String fileName) throws Exception {
        byte[] body = mvc.perform(get(url).with(user(u)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString(
                        java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"))))
                .andReturn().getResponse().getContentAsByteArray();
        return new XSSFWorkbook(new ByteArrayInputStream(body));
    }

    private static List<String> rowValues(Sheet s, int rowIndex, int from, int to) {
        DataFormatter f = new DataFormatter();
        Row row = s.getRow(rowIndex);
        List<String> out = new ArrayList<>();
        for (int c = from; c <= to; c++) {
            Cell cell = row == null ? null : row.getCell(c);
            out.add(cell == null ? "" : f.formatCellValue(cell));
        }
        return out;
    }

    @Test
    void 체크리스트는_원본과_같은_위치와_헤더로_내보낸다() throws Exception {
        try (XSSFWorkbook wb = download("/checklist/export?year=2026", lead, "2_IT자체감사_체크리스트_2026.xlsx")) {
            Sheet s = wb.getSheet("IT자체감사 체크리스트");
            assertThat(rowValues(s, 2, 1, 6)).containsExactly("순번", "감사 영역", "감사 항목", "항목 상세", "감사 시기", "법규/규정");
            assertThat(s.getLastRowNum()).isEqualTo(2 + 43);
            assertThat(rowValues(s, 3, 1, 2)).containsExactly("1", "IT시스템 운영 및 유지보수");
            // 감사 영역 병합: IT시스템 운영 및 유지보수 #1~#22 (원본 C4:C25)
            assertThat(s.getMergedRegions()).anyMatch(m -> m.formatAsString().equals("C4:C25"));
        }
    }

    @Autowired com.itaudit.checklist.ChecklistExcelParser parser;

    @Test
    void 내보낸_체크리스트를_다시_올리면_원본과_같은_43건으로_읽힌다() throws Exception {
        byte[] exported = mvc.perform(get("/checklist/export?year=2026").with(user(lead)))
                .andReturn().getResponse().getContentAsByteArray();
        var again = parser.parse(new ByteArrayInputStream(exported), "export.xlsx", 2027);
        try (var in = java.nio.file.Files.newInputStream(com.itaudit.support.TestFiles.CHECKLIST_XLSX)) {
            var original = parser.parse(in, "orig.xlsx", 2027);
            assertThat(again.isValid()).isTrue();
            assertThat(again.getRows()).hasSize(43);
            for (int i = 0; i < 43; i++) {
                var a = again.getRows().get(i);
                var o = original.getRows().get(i);
                assertThat(List.of(a.getSeqNo(), a.getAuditArea(), a.getAuditItem(), a.getItemDetail(), a.getAuditPeriod(), a.getRegulation()))
                        .isEqualTo(List.of(o.getSeqNo(), o.getAuditArea(), o.getAuditItem(), o.getItemDetail(), o.getAuditPeriod(), o.getRegulation()));
            }
        }
    }

    @Test
    void 총괄표는_제외_항목을_빼고_조치내용과_개선예정일을_한_칸으로_합친다() throws Exception {
        try (XSSFWorkbook wb = download("/rounds/" + roundId + "/export", lead, "3_2026_3분기_IT자체감사_총괄표.xlsx")) {
            Sheet s = wb.getSheet("2026_3Q");
            assertThat(rowValues(s, 3, 1, 13)).containsExactly("No", "감사 영역", "평가항목", "법규/규정", "점검방식", "점검시기",
                    "담당팀", "담당자", "사전요청자료", "점검결과", "점검 내용 상세", "비고1", "조치내용 및 개선 예정일");
            assertThat(s.getLastRowNum()).as("15건 중 #25 제외 = 14행").isEqualTo(3 + 14);
            assertThat(rowValues(s, 1, 8, 8)).containsExactly(": 회신불필요");
            // #23 은 회차 안 4번째 (7, 21, 22, 23)
            assertThat(rowValues(s, 7, 3, 13)).containsExactly("개인(신용)정보 관리 보호",
                    rowValues(s, 7, 4, 4).get(0), rowValues(s, 7, 5, 5).get(0), "3분기", "인프라팀", "x-kim 사용자",
                    "계정삭제 수행 이력 (표본 5개)", "미흡", "1건 미회수", "", "회수 절차 보완\n(개선 예정일 2026-12-31)");
        }
    }

    @Test
    void 사전요청자료는_제목과_헤더를_원본처럼_쓴다() throws Exception {
        testRounds.submitToDept(roundId);
        testRounds.confirmFinding(item23, dept.getUserId(), "INSUFFICIENT", "미삭제", "26년 4분기 내 조치");
        try (XSSFWorkbook wb = download("/requests/export?roundId=" + roundId, dept, "4_2026_3분기_적정성점검_사전요청자료.xlsx")) {
            Sheet s = wb.getSheetAt(0);
            assertThat(rowValues(s, 0, 1, 1)).containsExactly("■ 2026년 3분기 IT자체감사 적정성 점검 사전요청자료");
            assertThat(rowValues(s, 2, 1, 6)).containsExactly("No", "감사 영역", "평가항목", "점검방식", "사전요청자료", "비고");
            assertThat(rowValues(s, 3, 6, 6)).containsExactly("26년 4분기 내 조치");
        }
        mvc.perform(get("/requests/export?roundId=" + roundId).with(user(kim))).andExpect(status().isForbidden());
    }

    @Test
    void 관리대장은_두_시트로_내보내고_여러_항목에_걸친_개선은_병합한다() throws Exception {
        testRounds.submitToDept(roundId);
        testRounds.confirmFinding(item23, dept.getUserId(), "INSUFFICIENT", "미삭제", null);
        testRounds.confirmFinding(item26, dept.getUserId(), "RISK", "반출 승인 없음", null);
        jdbc.update("INSERT INTO improvement (kind, round_id, team_code, assignee_id, inspection_result, detail, improvement_plan, "
                + "planned_date, action_result, closed_yn, created_by, created_at, updated_at) VALUES ('IMPROVEMENT', ?, 'INFRA', ?, "
                + "'RISK', '계정 미삭제, 반출 승인 없음', '절차 수립', DATE '2026-12-31', 'IN_PROGRESS', 'N', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                roundId, kim.getUserId(), lead.getUserId());
        Long impId = jdbc.queryForObject("SELECT MAX(improvement_id) FROM improvement", Long.class);
        jdbc.update("INSERT INTO improvement_item (improvement_id, round_item_id) VALUES (?, ?), (?, ?)", impId, item23, impId, item26);

        try (XSSFWorkbook wb = download("/improvements/export", lead, "5_IT자체감사_개선사항_관리대장.xlsx")) {
            Sheet s = wb.getSheet("개선사항 관리대장");
            assertThat(rowValues(s, 1, 2, 2)).containsExactly("IT자체감사 개선사항 관리대장");
            assertThat(rowValues(s, 3, 1, 13)).containsExactly("No", "감사 영역", "감사 항목", "항목 상세", "감사 시기",
                    "담당부서/담당자", "점검 결과", "점검 내용 상세", "개선 방안", "개선예정일", "조치 결과", "조치 현황", "증빙자료");
            assertThat(rowValues(s, 4, 3, 3)).containsExactly("개인(신용)정보 관리 보호");
            assertThat(rowValues(s, 5, 3, 3)).containsExactly("반출입 통제");
            assertThat(rowValues(s, 4, 5, 11)).containsExactly("2026 3Q", "인프라팀 / x-kim 사용자", "위험",
                    "계정 미삭제, 반출 승인 없음", "절차 수립", "2026-12-31", "진행중");
            assertThat(s.getMergedRegions()).anyMatch(m -> m.formatAsString().equals("J5:J6"));
            assertThat(s.getDataValidations()).hasSize(2);

            Sheet rec = wb.getSheet("권고사항");
            assertThat(rowValues(rec, 3, 1, 12)).containsExactly("No", "감사 영역", "감사 항목", "항목 상세", "감사 시기",
                    "담당부서/담당자", "상세 내용", "개선 방안", "개선예정일", "조치 결과", "조치 현황", "증빙자료");
        }
    }

    @Test
    void 감사부서는_제출_전_회차_총괄표를_내려받을_수_없다() throws Exception {
        mvc.perform(get("/rounds/" + roundId + "/export").with(user(dept))).andExpect(status().isForbidden());
        mvc.perform(get("/checklist/export?year=2026").with(user(dept))).andExpect(status().isForbidden());
        mvc.perform(get("/checklist/export?year=2031").with(user(lead))).andExpect(status().isBadRequest());
    }
}
