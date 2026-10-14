package com.itaudit.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.support.TestFiles;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChecklistControllerTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired ChecklistMapper mapper;
    @Autowired ActivityLogMapper activityLogMapper;

    LoginUser lead;
    MockHttpSession session;

    @BeforeEach
    void setUp() {
        lead = testUsers.user("cl-lead", Role.AUDIT_LEAD);
        session = new MockHttpSession();
    }

    private MockMultipartFile originalFile() throws Exception {
        return new MockMultipartFile("file", "2_IT자체감사_체크리스트.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                TestFiles.bytes(TestFiles.CHECKLIST_XLSX));
    }

    private void uploadAndConfirm(int year) throws Exception {
        mvc.perform(multipart("/checklist/upload").file(originalFile()).param("year", String.valueOf(year))
                        .session(session).with(user(lead)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/checklist/upload/confirm").session(session).with(user(lead)).with(csrf()))
                .andExpect(redirectedUrl("/checklist?year=" + year));
    }

    @Test
    void 엑셀을_올리면_미리보기를_보여주고_확정하면_43건이_저장된다() throws Exception {
        mvc.perform(multipart("/checklist/upload").file(originalFile()).param("year", "2026")
                        .session(session).with(user(lead)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("43건 등록 확정")));
        assertThat(mapper.countByYear(2026)).as("확정 전에는 저장하지 않음").isZero();

        mvc.perform(post("/checklist/upload/confirm").session(session).with(user(lead)).with(csrf()))
                .andExpect(redirectedUrl("/checklist?year=2026"));

        assertThat(mapper.countByYear(2026)).isEqualTo(43);
        assertThat(mapper.findByYear(2026, null, AuditPeriod.Q3)).hasSize(10);
        assertThat(mapper.findByYear(2026, null, AuditPeriod.EVERY)).extracting(ChecklistItem::getSeqNo)
                .containsExactly(7, 21, 22, 34, 42);
        ChecklistItem first = mapper.findBySeqNo(2026, 1);
        assertThat(first.getAuditArea()).isEqualTo(AuditArea.IT_OPS);
        assertThat(first.getCreatedBy()).isEqualTo(lead.getUserId());
        assertThat(activityLogMapper.findByAction(ActivityLogService.CHECKLIST_UPLOAD))
                .anyMatch(l -> "2026".equals(l.getTargetId()) && l.getDetail().contains("43건"));
        assertThat(session.getAttribute(ChecklistController.SESSION_UPLOAD)).isNull();
    }

    @Test
    void 목록_화면은_감사_영역과_감사_항목을_합쳐_보여주고_필터가_동작한다() throws Exception {
        uploadAndConfirm(2026);

        String html = mvc.perform(get("/checklist").param("year", "2026").with(user(lead)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("항목 상세 43건 · 감사 항목 20개");
        // 감사 영역 IT시스템 운영 및 유지보수 = #1~#22
        assertThat(html).contains("rowspan=\"22\"");

        mvc.perform(get("/checklist").param("year", "2026").param("period", "EVERY").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("항목 상세 5건")))
                .andExpect(content().string(Matchers.containsString("(전체 43건 중)")));
    }

    @Test
    void 같은_연도에_다시_올리면_기존_항목을_대체한다() throws Exception {
        uploadAndConfirm(2026);
        mvc.perform(multipart("/checklist/upload").file(originalFile()).param("year", "2026")
                        .session(session).with(user(lead)).with(csrf()))
                .andExpect(content().string(Matchers.containsString("이미 43건이 있습니다")));
        mvc.perform(post("/checklist/upload/confirm").session(session).with(user(lead)).with(csrf()));

        assertThat(mapper.countByYear(2026)).isEqualTo(43);
        assertThat(activityLogMapper.findByAction(ActivityLogService.CHECKLIST_UPLOAD))
                .anyMatch(l -> l.getDetail().contains("기존 43건 대체"));
    }

    @Test
    void xlsx가_아닌_파일은_거부한다() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "seed.csv", "text/csv",
                TestFiles.bytes(TestFiles.CHECKLIST_SEED_CSV));
        mvc.perform(multipart("/checklist/upload").file(csv).param("year", "2026")
                        .session(session).with(user(lead)).with(csrf()))
                .andExpect(content().string(Matchers.containsString(".xlsx 파일만")));
    }

    @Test
    void 오류가_있는_업로드는_확정할_수_없다() throws Exception {
        MockMultipartFile broken = new MockMultipartFile("file", "broken.xlsx", null, "not excel".getBytes());
        mvc.perform(multipart("/checklist/upload").file(broken).param("year", "2026")
                        .session(session).with(user(lead)).with(csrf()))
                .andExpect(content().string(Matchers.containsString("엑셀 파일을 읽을 수 없습니다")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("등록 확정"))));
        mvc.perform(post("/checklist/upload/confirm").session(session).with(user(lead)).with(csrf()))
                .andExpect(redirectedUrl("/checklist/upload?year=2026"));
        assertThat(mapper.countByYear(2026)).isZero();
    }

    @Test
    void 연도를_복사해_새_개정본을_만든다() throws Exception {
        uploadAndConfirm(2026);

        mvc.perform(post("/checklist/copy").param("fromYear", "2026").param("toYear", "2027")
                        .with(user(lead)).with(csrf()))
                .andExpect(redirectedUrl("/checklist?year=2027"));
        assertThat(mapper.countByYear(2027)).isEqualTo(43);
        assertThat(mapper.findBySeqNo(2027, 42).getItemDetail()).isEqualTo(mapper.findBySeqNo(2026, 42).getItemDetail());

        // 이미 있는 연도로는 복사 불가
        mvc.perform(post("/checklist/copy").param("fromYear", "2026").param("toYear", "2027")
                        .with(user(lead)).with(csrf()))
                .andExpect(redirectedUrl("/checklist?year=2026"));
        assertThat(mapper.countByYear(2027)).isEqualTo(43);
    }

    @Test
    void 항목을_개별로_추가하고_수정하며_이력이_남는다() throws Exception {
        mvc.perform(post("/checklist/items").param("year", "2028").with(user(lead)).with(csrf())
                        .param("seqNo", "1").param("auditArea", "SECURITY").param("auditItem", "접근관리")
                        .param("itemDetail", "계정 점검").param("auditPeriod", "Q2").param("regulation", "규정 A")
                        .param("active", "true"))
                .andExpect(redirectedUrl("/checklist?year=2028"));
        ChecklistItem item = mapper.findBySeqNo(2028, 1);
        assertThat(item.getAuditPeriod()).isEqualTo(AuditPeriod.Q2);

        // 같은 연도 같은 순번은 거부
        mvc.perform(post("/checklist/items").param("year", "2028").with(user(lead)).with(csrf())
                        .param("seqNo", "1").param("auditArea", "SECURITY").param("auditItem", "x")
                        .param("itemDetail", "y").param("auditPeriod", "Q1"))
                .andExpect(content().string(Matchers.containsString("이미 있습니다")));

        // 수정: 사용 해제 (삭제 대신)
        mvc.perform(post("/checklist/items/" + item.getItemId()).with(user(lead)).with(csrf())
                        .param("seqNo", "1").param("auditArea", "SECURITY").param("auditItem", "접근관리")
                        .param("itemDetail", "계정 점검 (개정)").param("auditPeriod", "EVERY").param("regulation", "")
                        // 체크박스 해제 시 브라우저는 th:field 가 만든 숨김 필드(_active)만 보낸다
                        .param("_active", "on"))
                .andExpect(redirectedUrl("/checklist?year=2028"));
        ChecklistItem updated = mapper.findById(item.getItemId());
        assertThat(updated.getItemDetail()).isEqualTo("계정 점검 (개정)");
        assertThat(updated.getAuditPeriod()).isEqualTo(AuditPeriod.EVERY);
        assertThat(updated.getRegulation()).isNull();
        assertThat(updated.isActive()).isFalse();
        assertThat(activityLogMapper.findByAction(ActivityLogService.CHECKLIST_UPDATE))
                .anyMatch(l -> String.valueOf(item.getItemId()).equals(l.getTargetId())
                        && l.getDetail().contains("계정 점검 (개정)"));
    }

    @Test
    void 입력값이_비면_오류를_보여준다() throws Exception {
        mvc.perform(post("/checklist/items").param("year", "2028").with(user(lead)).with(csrf())
                        .param("seqNo", "").param("auditItem", "").param("itemDetail", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("순번을 입력하세요")))
                .andExpect(content().string(Matchers.containsString("감사 영역을 선택하세요")));
        assertThat(mapper.countByYear(2028)).isZero();
    }

    @Test
    void 관리자도_체크리스트를_관리할_수_있고_다른_역할은_접근할_수_없다() throws Exception {
        LoginUser admin = testUsers.user("cl-admin", Role.ADMIN);
        mvc.perform(get("/checklist").with(user(admin))).andExpect(status().isOk());

        for (Role role : new Role[]{Role.INSPECTOR, Role.AUDIT_DEPT, Role.VIEWER}) {
            LoginUser u = testUsers.user("cl-" + role.name().toLowerCase(), role);
            mvc.perform(get("/checklist").with(user(u))).andExpect(status().isForbidden());
            mvc.perform(multipart("/checklist/upload").file(originalFile()).param("year", "2026")
                            .with(user(u)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertThat(mapper.countByYear(2026)).isZero();
    }

    @Test
    void 빈_연도는_업로드_안내를_보여준다() throws Exception {
        mvc.perform(get("/checklist").param("year", "2030").with(user(lead)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("2030년 체크리스트가 없습니다")));
        mvc.perform(get("/checklist/upload").with(user(lead))).andExpect(status().isOk());
        mvc.perform(get("/checklist/items/new").param("year", "2030").with(user(lead))).andExpect(status().isOk());
    }
}
