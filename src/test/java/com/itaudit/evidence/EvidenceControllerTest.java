package com.itaudit.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvidenceControllerTest {

    @Test
    void 사이트_밖으로_돌아가는_주소는_막는다() {
        assertThat(EvidenceController.safeReturnUrl("/inspections/1")).isEqualTo("/inspections/1");
        assertThat(EvidenceController.safeReturnUrl("https://evil.example")).isEqualTo("/");
        assertThat(EvidenceController.safeReturnUrl("//evil.example")).isEqualTo("/");
        assertThat(EvidenceController.safeReturnUrl("/\\evil")).isEqualTo("/");
        assertThat(EvidenceController.safeReturnUrl(null)).isEqualTo("/");
    }

    @Test
    void 파일명에서_경로를_떼고_확장자를_소문자로_본다() {
        var f = new org.springframework.mock.web.MockMultipartFile("files", "C:\\temp\\증적.PDF", null, new byte[]{1});
        assertThat(EvidenceService.originalName(f)).isEqualTo("증적.PDF");
        assertThat(EvidenceService.extension("증적.PDF")).isEqualTo("pdf");
        assertThat(EvidenceService.extension("noext")).isEmpty();
    }
}
