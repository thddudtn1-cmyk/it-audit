package com.itaudit.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.checklist.ChecklistMapper;
import com.itaudit.checklist.ChecklistService;
import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoundControllerTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired RoundMapper roundMapper;
    @Autowired ChecklistMapper checklistMapper;
    @Autowired ChecklistService checklistService;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired JdbcTemplate jdbc;

    LoginUser lead;
    LoginUser infraKim;
    LoginUser devLee;

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("r-lead", Role.AUDIT_LEAD);
        testUsers.team("INFRA", "인프라팀");
        testUsers.team("DEV", "개발팀");
        infraKim = testUsers.inspector("r-kim", "INFRA");
        devLee = testUsers.inspector("r-lee", "DEV");
        testChecklist.load(2026, lead);
    }

    private Long checklistItemId(int year, int seqNo) {
        return checklistMapper.findBySeqNo(year, seqNo).getItemId();
    }

    /** 회차 생성 요청. excluded: 순번 → 제외 사유 */
    private MockHttpServletRequestBuilder createRequest(int year, int quarter, Map<Integer, String> excluded) {
        MockHttpServletRequestBuilder req = post("/rounds").with(user(lead)).with(csrf())
                .param("year", String.valueOf(year)).param("quarter", String.valueOf(quarter));
        int idx = 0;
        for (Map.Entry<Integer, String> e : excluded.entrySet()) {
            req.param("rows[" + idx + "].checklistItemId", String.valueOf(checklistItemId(year, e.getKey())))
                    .param("rows[" + idx + "].excluded", "true")
                    .param("rows[" + idx + "].excludeReason", e.getValue());
            idx++;
        }
        return req;
    }

    private Long createRound(int year, int quarter, Map<Integer, String> excluded) throws Exception {
        mvc.perform(createRequest(year, quarter, excluded)).andExpect(redirectedUrlPattern("/rounds/*"));
        return roundMapper.findRoundByQuarter(year, quarter).getRoundId();
    }

    private Map<Integer, RoundItem> itemsBySeq(Long roundId) {
        return roundMapper.findItems(roundId).stream().collect(Collectors.toMap(RoundItem::getSeqNo, i -> i));
    }

    // ---------- 생성 ----------

    @Test
    void 연도_분기를_고르면_해당_분기와_매분기_항목을_추출한다() throws Exception {
        String html = mvc.perform(get("/rounds/new").param("year", "2026").param("quarter", "3").with(user(lead)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("15건").contains("2026년 3분기 회차 생성");

        // PRD 3장: 3분기 #23~#32 + 매분기 #7, #21, #22, #34, #42
        List<RoundCandidate> candidates = new RoundService(roundMapper, checklistMapper, null, null, null)
                .extractCandidates(2026, 3);
        assertThat(candidates).extracting(c -> c.item().getSeqNo())
                .containsExactly(7, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 34, 42);
    }

    @Test
    void 분기별_추출_건수가_PRD와_같다() {
        RoundService service = new RoundService(roundMapper, checklistMapper, null, null, null);
        assertThat(service.extractCandidates(2026, 1)).hasSize(11);
        assertThat(service.extractCandidates(2026, 2)).hasSize(18);
        assertThat(service.extractCandidates(2026, 3)).hasSize(15);
        assertThat(service.extractCandidates(2026, 4)).hasSize(14);
    }

    @Test
    void 제외_사유와_함께_회차를_만들면_3분기_14건이_된다() throws Exception {
        Long roundId = createRound(2026, 3, Map.of(25, "그룹 IT감사로 대체"));

        AuditRound round = roundMapper.findRound(roundId);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.DRAFT);
        assertThat(round.getTotalCount()).isEqualTo(15);
        assertThat(round.getTargetCount()).isEqualTo(14);
        RoundItem r25 = itemsBySeq(roundId).get(25);
        assertThat(r25.isExcluded()).isTrue();
        assertThat(r25.getExcludeReason()).isEqualTo("그룹 IT감사로 대체");
        assertThat(itemsBySeq(roundId).values()).allMatch(i -> i.getSubmitStatus() == SubmitStatus.NOT_SUBMITTED);

        String html = mvc.perform(get("/rounds/" + roundId).with(user(lead)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("2026년 3분기 IT자체감사 총괄표").contains("제외 항목 1건").contains("그룹 IT감사로 대체")
                .contains("평가항목").contains("점검방식").contains("점검시기");
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_CREATE))
                .anyMatch(l -> l.getDetail().contains("추출 15건") && l.getDetail().contains("제외 #25"));
    }

    @Test
    void 제외_사유가_없으면_만들지_않는다() throws Exception {
        mvc.perform(createRequest(2026, 3, Map.of(25, " ")))
                .andExpect(redirectedUrl("/rounds/new?year=2026&quarter=3"))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("제외 사유를 입력하세요: #25")));
        assertThat(roundMapper.findRoundByQuarter(2026, 3)).isNull();
    }

    @Test
    void 같은_분기_회차는_하나만_만들_수_있다() throws Exception {
        createRound(2026, 3, Map.of());
        mvc.perform(createRequest(2026, 3, Map.of()))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("이미 있습니다")));
        mvc.perform(get("/rounds/new").param("year", "2026").param("quarter", "3").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("회차가 이미 있습니다")));
    }

    @Test
    void 체크리스트가_없는_연도는_만들_수_없다() throws Exception {
        mvc.perform(get("/rounds/new").param("year", "2031").param("quarter", "1").with(user(lead)))
                .andExpect(content().string(Matchers.not(Matchers.containsString("회차 생성</button>"))));
        mvc.perform(createRequest(2031, 1, Map.of()))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("체크리스트를 먼저 등록하세요")));
    }

    @Test
    void 사용하지_않는_체크리스트_항목은_추출하지_않는다() {
        jdbc.update("UPDATE checklist_item SET use_yn = 'N' WHERE apply_year = 2026 AND seq_no = 23");
        RoundService service = new RoundService(roundMapper, checklistMapper, null, null, null);
        assertThat(service.extractCandidates(2026, 3)).extracting(c -> c.item().getSeqNo()).doesNotContain(23).hasSize(14);
    }

    // ---------- 지난 회차 배정 복사 ----------

    @Test
    void 지난_회차의_같은_순번에서_배정과_제외를_가져온다() throws Exception {
        // 2025년 3분기 (분기 항목의 지난 값) + 2026년 2분기 (매분기 항목의 직전 값)
        checklistService.copyYear(2026, 2025, lead);
        Long r2025q3 = createRound(2025, 3, Map.of(25, "그룹 IT감사로 대체"));
        Long r2026q2 = createRound(2026, 2, Map.of());
        assign(r2025q3, 23, "INFRA", infraKim.getUserId(), "접근권한 변경 대장", 3, false);
        assign(r2025q3, 7, "INFRA", infraKim.getUserId(), "2025 변경 승인 내역", null, false);
        assign(r2026q2, 7, "DEV", devLee.getUserId(), "ICMS 시스템변경 랜덤 5개", 5, false);
        assign(r2026q2, 21, "DEV", null, null, null, true);

        // 미리보기: #25 는 제외가 미리 체크됨
        mvc.perform(get("/rounds/new").param("year", "2026").param("quarter", "3").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("value=\"그룹 IT감사로 대체\"")));

        Long roundId = createRound(2026, 3, Map.of(25, "그룹 IT감사로 대체"));
        Map<Integer, RoundItem> items = itemsBySeq(roundId);
        // 매분기 #7: 바로 앞 2026년 2분기 값
        assertThat(items.get(7).getAssigneeId()).isEqualTo(devLee.getUserId());
        assertThat(items.get(7).getRequestMaterial()).isEqualTo("ICMS 시스템변경 랜덤 5개");
        assertThat(items.get(7).getSampleSize()).isEqualTo(5);
        assertThat(items.get(21).isNoReply()).isTrue();
        // 3분기 #23: 지난해 3분기 값
        assertThat(items.get(23).getTeamCode()).isEqualTo("INFRA");
        assertThat(items.get(23).getAssigneeId()).isEqualTo(infraKim.getUserId());
        assertThat(items.get(23).getSampleSize()).isEqualTo(3);
        // 지난 회차가 없는 항목은 비어 있음
        assertThat(items.get(24).getAssigneeId()).isNull();
    }

    @Test
    void 복사를_끄면_배정을_가져오지_않는다() throws Exception {
        Long r2026q2 = createRound(2026, 2, Map.of());
        assign(r2026q2, 7, "DEV", devLee.getUserId(), "자료", 5, false);

        mvc.perform(createRequest(2026, 3, Map.of()).param("_copyPrevious", "on"));
        Long roundId = roundMapper.findRoundByQuarter(2026, 3).getRoundId();
        assertThat(itemsBySeq(roundId).get(7).getAssigneeId()).isNull();
        assertThat(itemsBySeq(roundId).get(7).getRequestMaterial()).isNull();
    }

    @Test
    void 지금은_쓸_수_없는_담당자는_복사하지_않는다() throws Exception {
        Long r2026q2 = createRound(2026, 2, Map.of());
        assign(r2026q2, 7, "DEV", devLee.getUserId(), "자료", null, false);
        jdbc.update("UPDATE app_user SET use_yn = 'N' WHERE user_id = ?", devLee.getUserId());

        Long roundId = createRound(2026, 3, Map.of());
        RoundItem r7 = itemsBySeq(roundId).get(7);
        assertThat(r7.getTeamCode()).isEqualTo("DEV");
        assertThat(r7.getAssigneeId()).isNull();
        assertThat(r7.getRequestMaterial()).isEqualTo("자료");
    }

    // ---------- 배정 편집 ----------

    /** 배정 편집 화면처럼 회차 전체 행을 보내되, 지정한 순번만 값을 바꾼다 */
    private void assign(Long roundId, int seqNo, String team, Long assigneeId, String material, Integer sample,
                        boolean noReply) throws Exception {
        mvc.perform(assignRequest(roundId, seqNo, team, assigneeId, material, sample, noReply, false, null))
                .andExpect(redirectedUrl("/rounds/" + roundId));
    }

    private MockHttpServletRequestBuilder assignRequest(Long roundId, int seqNo, String team, Long assigneeId,
                                                        String material, Integer sample, boolean noReply,
                                                        boolean excluded, String reason) {
        MockHttpServletRequestBuilder req = post("/rounds/" + roundId + "/assign").with(user(lead)).with(csrf());
        List<RoundItem> items = roundMapper.findItems(roundId);
        for (int i = 0; i < items.size(); i++) {
            RoundItem it = items.get(i);
            String p = "rows[" + i + "].";
            req.param(p + "roundItemId", String.valueOf(it.getRoundItemId()));
            if (it.getSeqNo() == seqNo) {
                req.param(p + "teamCode", team == null ? "" : team)
                        .param(p + "assigneeId", assigneeId == null ? "" : String.valueOf(assigneeId))
                        .param(p + "requestMaterial", material == null ? "" : material)
                        .param(p + "sampleSize", sample == null ? "" : String.valueOf(sample));
                if (noReply) {
                    req.param(p + "noReply", "true");
                }
                if (excluded) {
                    req.param(p + "excluded", "true");
                }
                req.param(p + "excludeReason", reason == null ? "" : reason);
            } else {
                req.param(p + "teamCode", it.getTeamCode() == null ? "" : it.getTeamCode())
                        .param(p + "assigneeId", it.getAssigneeId() == null ? "" : String.valueOf(it.getAssigneeId()))
                        .param(p + "requestMaterial", it.getRequestMaterial() == null ? "" : it.getRequestMaterial())
                        .param(p + "sampleSize", it.getSampleSize() == null ? "" : String.valueOf(it.getSampleSize()))
                        .param(p + "excludeReason", it.getExcludeReason() == null ? "" : it.getExcludeReason());
                if (it.isNoReply()) {
                    req.param(p + "noReply", "true");
                }
                if (it.isExcluded()) {
                    req.param(p + "excluded", "true");
                }
            }
        }
        return req;
    }

    @Test
    void 담당자를_배정하면_팀이_채워지고_변경_항목만_이력이_남는다() throws Exception {
        Long roundId = createRound(2026, 3, Map.of());
        mvc.perform(get("/rounds/" + roundId + "/assign").with(user(lead))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("r-kim 사용자 (인프라팀)")));

        // 팀을 비워 보내도 담당자의 팀으로 채움
        mvc.perform(assignRequest(roundId, 24, null, infraKim.getUserId(), "계정 목록", 2, false, false, null))
                .andExpect(flash().attribute("message", "1개 항목의 배정을 저장했습니다."));

        RoundItem r24 = itemsBySeq(roundId).get(24);
        assertThat(r24.getTeamCode()).isEqualTo("INFRA");
        assertThat(r24.getAssigneeName()).isEqualTo("r-kim 사용자");
        assertThat(roundMapper.findRound(roundId).getAssignedCount()).isEqualTo(1);
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_ITEM_ASSIGN))
                .hasSize(1).allMatch(l -> l.getDetail().contains("#24"));

        // 같은 값으로 다시 저장하면 변경 없음
        mvc.perform(assignRequest(roundId, 24, "INFRA", infraKim.getUserId(), "계정 목록", 2, false, false, null))
                .andExpect(flash().attribute("message", "바뀐 내용이 없습니다."));
    }

    @Test
    void 담당자가_선택한_팀_소속이_아니면_거부한다() throws Exception {
        Long roundId = createRound(2026, 3, Map.of());
        mvc.perform(assignRequest(roundId, 24, "DEV", infraKim.getUserId(), null, null, false, false, null))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("#24 담당자 r-kim 사용자은(는) 선택한 담당팀 소속이 아닙니다")));
        assertThat(itemsBySeq(roundId).get(24).getAssigneeId()).isNull();
    }

    @Test
    void 점검_담당자가_아닌_사용자는_배정할_수_없다() throws Exception {
        Long roundId = createRound(2026, 3, Map.of());
        mvc.perform(assignRequest(roundId, 24, null, lead.getUserId(), null, null, false, false, null))
                .andExpect(content().string(Matchers.containsString("사용 중인 점검 담당자여야 합니다")));
    }

    @Test
    void 배정_화면에서_제외하려면_사유가_필요하고_다시_포함할_수_있다() throws Exception {
        Long roundId = createRound(2026, 3, Map.of());
        mvc.perform(assignRequest(roundId, 30, null, null, null, null, false, true, ""))
                .andExpect(content().string(Matchers.containsString("#30 제외 사유를 입력하세요")));

        mvc.perform(assignRequest(roundId, 30, null, null, null, null, false, true, "해당 분기 신규 업무 없음"))
                .andExpect(redirectedUrl("/rounds/" + roundId));
        assertThat(roundMapper.findRound(roundId).getTargetCount()).isEqualTo(14);

        mvc.perform(assignRequest(roundId, 30, null, null, null, null, false, false, "해당 분기 신규 업무 없음"))
                .andExpect(redirectedUrl("/rounds/" + roundId));
        RoundItem r30 = itemsBySeq(roundId).get(30);
        assertThat(r30.isExcluded()).isFalse();
        assertThat(r30.getExcludeReason()).isNull();
    }

    @Test
    void 감사부서에_제출된_회차는_배정을_바꿀_수_없다() throws Exception {
        Long roundId = createRound(2026, 3, Map.of());
        jdbc.update("UPDATE audit_round SET status = 'SUBMITTED' WHERE round_id = ?", roundId);

        mvc.perform(get("/rounds/" + roundId + "/assign").with(user(lead))).andExpect(redirectedUrl("/rounds/" + roundId));
        mvc.perform(assignRequest(roundId, 24, null, infraKim.getUserId(), null, null, false, false, null))
                .andExpect(content().string(Matchers.containsString("배정을 바꿀 수 없습니다")));
        mvc.perform(get("/rounds/" + roundId).with(user(lead)))
                .andExpect(content().string(Matchers.not(Matchers.containsString("배정 편집"))));
    }

    // ---------- 권한 ----------

    @Test
    void 감사부서는_제출된_회차만_본다() throws Exception {
        LoginUser dept = testUsers.user("r-dept", Role.AUDIT_DEPT);
        Long roundId = createRound(2026, 3, Map.of());

        mvc.perform(get("/rounds").with(user(dept)))
                .andExpect(content().string(Matchers.containsString("감사부서에 제출된 회차가 없습니다")));
        mvc.perform(get("/rounds/" + roundId).with(user(dept))).andExpect(status().isForbidden());

        jdbc.update("UPDATE audit_round SET status = 'SUBMITTED' WHERE round_id = ?", roundId);
        mvc.perform(get("/rounds").with(user(dept)))
                .andExpect(content().string(Matchers.containsString("2026년 3분기")));
        mvc.perform(get("/rounds/" + roundId).with(user(dept))).andExpect(status().isOk());
    }

    @Test
    void 조회자는_보기만_하고_생성_배정은_주관자만_한다() throws Exception {
        LoginUser viewer = testUsers.user("r-viewer", Role.VIEWER);
        LoginUser dept = testUsers.user("r-dept2", Role.AUDIT_DEPT);
        Long roundId = createRound(2026, 3, Map.of());

        mvc.perform(get("/rounds").with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("회차 생성"))));
        mvc.perform(get("/rounds/" + roundId).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("배정 편집"))));

        for (LoginUser u : List.of(viewer, dept)) {
            mvc.perform(get("/rounds/new").with(user(u))).andExpect(status().isForbidden());
            mvc.perform(post("/rounds").param("year", "2026").param("quarter", "4").with(user(u)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/rounds/" + roundId + "/assign").with(user(u))).andExpect(status().isForbidden());
            mvc.perform(post("/rounds/" + roundId + "/assign").with(user(u)).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/rounds").with(user(infraKim))).andExpect(status().isForbidden());
        assertThat(roundMapper.findRoundByQuarter(2026, 4)).isNull();
    }

    // ---------- 체크리스트 연계 ----------

    @Test
    void 회차에_쓰인_연도의_체크리스트는_엑셀로_대체할_수_없다() throws Exception {
        createRound(2026, 3, Map.of());
        assertThat(checklistService.isUsedInRounds(2026)).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> testChecklist.load(2026, lead))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("대체할 수 없습니다");
        assertThat(checklistMapper.countByYear(2026)).isEqualTo(43);
    }
}
