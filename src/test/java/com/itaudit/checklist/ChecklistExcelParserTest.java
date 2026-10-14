package com.itaudit.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.itaudit.support.TestFiles;

class ChecklistExcelParserTest {

    private final ChecklistExcelParser parser = new ChecklistExcelParser();

    private ChecklistUploadPreview parseOriginal() throws Exception {
        try (InputStream in = Files.newInputStream(TestFiles.CHECKLIST_XLSX)) {
            return parser.parse(in, "②.xlsx", 2026);
        }
    }

    @Test
    void 체크리스트_원본_엑셀_43건을_오류없이_읽는다() throws Exception {
        ChecklistUploadPreview p = parseOriginal();

        assertThat(p.getFileErrors()).isEmpty();
        assertThat(p.getRows()).hasSize(43);
        assertThat(p.isValid()).isTrue();
        assertThat(p.getSheetName()).isEqualTo("IT자체감사 체크리스트");
    }

    @Test
    void 원본_엑셀을_읽은_값이_초기_데이터_CSV와_같다() throws Exception {
        List<ChecklistUploadRow> rows = parseOriginal().getRows();
        List<List<String>> csv = TestFiles.readCsv(TestFiles.CHECKLIST_SEED_CSV);
        assertThat(csv.get(0)).containsExactly("순번", "감사 영역", "감사 항목", "항목 상세", "감사 시기", "법규/규정");

        String prevItem = null;
        for (int i = 0; i < 43; i++) {
            List<String> expected = csv.get(i + 1);
            ChecklistUploadRow actual = rows.get(i);
            // CSV 는 #34~#40 감사 항목이 빈 칸 → 위 행 값을 이어받은 것과 비교
            String expectedItem = expected.get(2).isBlank() ? prevItem : expected.get(2).trim();
            prevItem = expectedItem;

            assertThat(actual.getSeqNo()).as("행 %d 순번", i).isEqualTo(Integer.parseInt(expected.get(0)));
            assertThat(actual.getAuditArea().getLabel()).as("#%s 감사 영역", expected.get(0)).isEqualTo(expected.get(1).trim());
            assertThat(actual.getAuditItem()).as("#%s 감사 항목", expected.get(0)).isEqualTo(expectedItem);
            assertThat(actual.getItemDetail()).as("#%s 항목 상세", expected.get(0)).isEqualTo(expected.get(3).trim());
            assertThat(actual.getAuditPeriod().getLabel()).as("#%s 감사 시기", expected.get(0)).isEqualTo(expected.get(4).trim());
            assertThat(actual.getRegulation()).as("#%s 법규/규정", expected.get(0)).isEqualTo(expected.get(5).trim());
        }
    }

    @Test
    void 감사_시기별_건수가_PRD_분기별_점검_대상과_같다() throws Exception {
        Map<AuditPeriod, Long> counts = parseOriginal().getPeriodCounts();
        // PRD 3장: 1분기 6, 2분기 13, 3분기 10, 4분기 9, 매분기 5
        assertThat(counts).containsEntry(AuditPeriod.Q1, 6L).containsEntry(AuditPeriod.Q2, 13L)
                .containsEntry(AuditPeriod.Q3, 10L).containsEntry(AuditPeriod.Q4, 9L)
                .containsEntry(AuditPeriod.EVERY, 5L);
        List<Integer> every = parseOriginal().getRows().stream()
                .filter(r -> r.getAuditPeriod() == AuditPeriod.EVERY).map(ChecklistUploadRow::getSeqNo).toList();
        assertThat(every).containsExactly(7, 21, 22, 34, 42);
    }

    @Test
    void 병합되지_않은_빈_감사_항목은_위_행_값을_이어받는다() throws Exception {
        List<ChecklistUploadRow> rows = parseOriginal().getRows();
        List<Integer> inherited = rows.stream().filter(ChecklistUploadRow::isAuditItemInherited)
                .map(ChecklistUploadRow::getSeqNo).toList();
        assertThat(inherited).containsExactly(34, 35, 36, 37, 38, 39, 40);
        assertThat(rows.get(33).getAuditItem()).isEqualTo("프로그램 등록·변경·폐기 절차 수립 및 운용");
        // 병합 셀 (#2~#5 감사 항목, #43 감사 항목) 은 이어받기가 아니라 병합 값
        assertThat(rows.get(1).isAuditItemInherited()).isFalse();
        assertThat(rows.get(42).getAuditItem()).isEqualTo("데이터변경 처리");
        assertThat(rows.get(42).isAuditItemInherited()).isFalse();

        long distinctItems = rows.stream().map(r -> r.getAuditArea() + "|" + r.getAuditItem()).distinct().count();
        assertThat(distinctItems).isEqualTo(20);
    }

    @Test
    void 항목_상세_안의_줄바꿈을_유지한다() throws Exception {
        ChecklistUploadRow r42 = parseOriginal().getRows().get(41);
        assertThat(r42.getSeqNo()).isEqualTo(42);
        assertThat(r42.getItemDetail()).contains("\n");
    }

    // ---------- 오류 ----------

    private static byte[] workbook(String[][] data, CellRangeAddress... merges) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("시트");
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < data[r].length; c++) {
                    if (data[r][c] != null) {
                        row.createCell(c).setCellValue(data[r][c]);
                    }
                }
            }
            for (CellRangeAddress m : merges) {
                sheet.addMergedRegion(m);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private ChecklistUploadPreview parse(byte[] bytes) {
        return parser.parse(new ByteArrayInputStream(bytes), "test.xlsx", 2026);
    }

    private static final String[] HEADER = {"순번", "감사 영역", "감사 항목", "항목 상세", "감사 시기", "법규/규정"};

    @Test
    void 잘못된_값은_행별_오류로_표시하고_저장할_수_없다() throws Exception {
        ChecklistUploadPreview p = parse(workbook(new String[][]{
                HEADER,
                {"1", "IT시스템 운영 및 유지보수", "항목A", "상세1", "1분기", "규정"},
                {"1", "IT시스템 운영 및 유지보수", "항목A", "상세2", "5분기", ""},
                {"x", "없는 영역", "항목B", "", "매분기", ""},
        }));

        assertThat(p.isValid()).isFalse();
        assertThat(p.getRows()).hasSize(3);
        assertThat(p.getRows().get(0).hasError()).isFalse();
        assertThat(p.getRows().get(1).getErrors())
                .anyMatch(e -> e.contains("중복")).anyMatch(e -> e.contains("감사 시기는"));
        assertThat(p.getRows().get(2).getErrors())
                .anyMatch(e -> e.contains("순번은")).anyMatch(e -> e.contains("알 수 없는 감사 영역"))
                .anyMatch(e -> e.contains("항목 상세가 비어"));
    }

    @Test
    void 헤더가_없거나_엑셀이_아니면_파일_오류() throws Exception {
        ChecklistUploadPreview noHeader = parse(workbook(new String[][]{{"a", "b"}, {"1", "2"}}));
        assertThat(noHeader.getFileErrors()).anyMatch(e -> e.contains("헤더 행을 찾지 못했습니다"));
        assertThat(noHeader.isValid()).isFalse();

        ChecklistUploadPreview notExcel = parse("hello".getBytes());
        assertThat(notExcel.getFileErrors()).anyMatch(e -> e.contains("엑셀 파일을 읽을 수 없습니다"));
    }

    @Test
    void 총괄표_헤더명과_띄어쓰기가_다른_영역명도_인식한다() throws Exception {
        ChecklistUploadPreview p = parse(workbook(new String[][]{
                {null, "제목 행"},
                {"No", "감사 영역", "평가항목", "점검방식", "점검시기", "법규/규정"},
                {"7", "시스템 개발 및 변경 관리", "데이터변경 처리", "상세", "매분기", ""},
                {"8", null, "", "상세2", "4분기", ""},
        }, new CellRangeAddress(2, 3, 1, 1)));

        assertThat(p.isValid()).isTrue();
        assertThat(p.getRows()).extracting(ChecklistUploadRow::getAuditArea)
                .containsOnly(AuditArea.DEV_CHANGE);
        assertThat(p.getRows().get(1).getAuditItem()).isEqualTo("데이터변경 처리");
        assertThat(p.getRows().get(1).getExcelRow()).isEqualTo(4);
        assertThat(p.getRows().stream().map(ChecklistUploadRow::getRegulation).collect(Collectors.toSet()))
                .containsOnly("");
    }
}
