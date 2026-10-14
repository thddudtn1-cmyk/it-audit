package com.itaudit.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.checklist.ChecklistMapper;
import com.itaudit.round.AuditRound;
import com.itaudit.round.RoundMapper;
import com.itaudit.support.TestChecklist;

/** 이미 올라온 체크리스트가 있으면 그것을 그대로 쓰고 내장 시드는 올리지 않는다 */
@SpringBootTest(properties = {"app.demo-login.enabled=true", "app.demo-data.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:itaudit-demo-existing;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Import(DemoDataSeederExistingChecklistTest.PreloadChecklist.class)
class DemoDataSeederExistingChecklistTest {

    static final int PRELOADED_YEAR = 2031;

    /** 시더(@Order(3))보다 먼저 체크리스트를 올려 두는 '사용자가 올린 체크리스트' 흉내 */
    @TestConfiguration
    static class PreloadChecklist {
        @org.springframework.context.annotation.Bean
        PreloadRunner preloadRunner(TestChecklist testChecklist) {
            return new PreloadRunner(testChecklist);
        }
    }

    @Order(0)
    static class PreloadRunner implements ApplicationRunner {
        private final TestChecklist testChecklist;

        PreloadRunner(TestChecklist testChecklist) {
            this.testChecklist = testChecklist;
        }

        @Override
        public void run(ApplicationArguments args) throws Exception {
            testChecklist.load(PRELOADED_YEAR, new LoginUser(0L, "preload", "preload", null, Role.AUDIT_LEAD, "", true, false));
        }
    }

    @Autowired RoundMapper roundMapper;
    @Autowired ChecklistMapper checklistMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 올라온_체크리스트_연도로_회차가_만들어지고_내장_시드는_올리지_않는다() {
        assertThat(checklistMapper.findYears()).as("올해 체크리스트를 따로 올리지 않음").containsExactly(PRELOADED_YEAR);
        assertThat(checklistMapper.countByYear(PRELOADED_YEAR)).isEqualTo(43);

        List<AuditRound> rounds = roundMapper.findRounds(null);
        assertThat(rounds).hasSize(3);
        assertThat(rounds).allMatch(r -> r.getRoundYear() == PRELOADED_YEAR);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM improvement", Integer.class)).isEqualTo(7);
    }
}
