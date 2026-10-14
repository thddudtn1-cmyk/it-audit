package com.itaudit.round;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditRoundTest {

    @Test
    void 제출률은_제외와_회신불필요를_뺀_항목_기준이다() {
        AuditRound r = new AuditRound();
        r.setRoundYear(2026);
        r.setQuarter(3);
        r.setTotalCount(15);
        r.setExcludedCount(1);
        r.setNoReplyCount(2);
        r.setSubmittedCount(6);

        assertThat(r.getLabel()).isEqualTo("2026년 3분기");
        assertThat(r.getShortLabel()).isEqualTo("2026 3Q");
        assertThat(r.getTargetCount()).isEqualTo(14);
        assertThat(r.getSubmitTargetCount()).isEqualTo(12);
        assertThat(r.getSubmitRate()).isEqualTo(50);
    }

    @Test
    void 제출_대상이_없으면_제출률은_0() {
        AuditRound r = new AuditRound();
        assertThat(r.getSubmitRate()).isZero();
    }
}
