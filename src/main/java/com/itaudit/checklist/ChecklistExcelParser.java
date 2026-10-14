package com.itaudit.checklist;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

/**
 * ② IT자체감사 체크리스트 엑셀을 읽는다.
 * - 헤더 행은 위치가 아니라 이름으로 찾는다 (순번·감사 영역·감사 항목·항목 상세·감사 시기·법규/규정).
 *   ③ 총괄표 표기(평가항목·점검방식·점검시기)도 같은 항목으로 받는다.
 * - 병합 셀은 병합 범위의 첫 칸 값을 쓴다.
 * - [가정] 감사 영역·감사 항목이 병합 없이 비어 있으면 위 행 값을 이어받는다.
 *   ② 원본의 #34~#40 감사 항목 칸이 병합 없이 비어 있고 서식상 #33에 이어지기 때문이다.
 */
@Component
public class ChecklistExcelParser {

    static final int MAX_ROWS = 2000;
    private static final int HEADER_SCAN_ROWS = 30;

    private static final String SEQ = "순번";
    private static final String AREA = "감사 영역";
    private static final String ITEM = "감사 항목";
    private static final String DETAIL = "항목 상세";
    private static final String PERIOD = "감사 시기";
    private static final String REGULATION = "법규/규정";

    /** 정규화한 헤더 텍스트 → 표준 항목명 */
    private static final Map<String, String> HEADER_ALIASES = new HashMap<>();

    static {
        alias(SEQ, "순번", "No");
        alias(AREA, "감사 영역");
        alias(ITEM, "감사 항목", "평가항목");
        alias(DETAIL, "항목 상세", "점검방식");
        alias(PERIOD, "감사 시기", "점검시기");
        alias(REGULATION, "법규/규정");
    }

    private static void alias(String standard, String... names) {
        for (String name : names) {
            HEADER_ALIASES.put(normalize(name), standard);
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }

    public ChecklistUploadPreview parse(InputStream in, String fileName, int applyYear) {
        ChecklistUploadPreview preview = new ChecklistUploadPreview();
        preview.setApplyYear(applyYear);
        preview.setFileName(fileName);

        try (Workbook wb = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            for (Sheet sheet : wb) {
                SheetReader reader = new SheetReader(sheet, formatter, evaluator);
                Header header = reader.findHeader();
                if (header != null) {
                    preview.setSheetName(sheet.getSheetName());
                    readRows(reader, header, preview);
                    return preview;
                }
            }
            preview.getFileErrors().add("헤더 행을 찾지 못했습니다. 첫 줄에 '순번, 감사 영역, 감사 항목, 항목 상세, 감사 시기, 법규/규정'이 있어야 합니다.");
        } catch (Exception e) {
            preview.getFileErrors().add("엑셀 파일을 읽을 수 없습니다. (.xlsx 파일인지 확인하세요)");
        }
        return preview;
    }

    private void readRows(SheetReader reader, Header header, ChecklistUploadPreview preview) {
        List<ChecklistUploadRow> rows = preview.getRows();
        Set<Integer> seqSeen = new HashSet<>();
        AuditArea prevArea = null;
        String prevAreaText = null;
        String prevItem = null;

        for (int r = header.rowIndex + 1; r <= reader.sheet.getLastRowNum(); r++) {
            String seqText = reader.value(r, header.col(SEQ));
            String detail = reader.value(r, header.col(DETAIL));
            String periodText = reader.value(r, header.col(PERIOD));
            // 순번·항목 상세·감사 시기가 모두 비면 데이터 행이 아니다 (빈 줄, 하단 메모)
            if (seqText.isEmpty() && detail.isEmpty() && periodText.isEmpty()) {
                continue;
            }
            if (rows.size() >= MAX_ROWS) {
                preview.getFileErrors().add("항목이 " + MAX_ROWS + "건을 넘습니다.");
                return;
            }

            ChecklistUploadRow row = new ChecklistUploadRow();
            row.setExcelRow(r + 1);
            row.setSeqText(seqText);
            row.setItemDetail(detail);
            row.setPeriodText(periodText);
            row.setRegulation(reader.value(r, header.col(REGULATION)));

            // 순번
            Integer seq = parseSeq(seqText);
            if (seq == null) {
                row.addError(seqText.isEmpty() ? "순번이 비어 있습니다." : "순번은 1 이상의 정수여야 합니다.");
            } else if (!seqSeen.add(seq)) {
                row.addError("순번 " + seq + "이(가) 중복됩니다.");
            }
            row.setSeqNo(seq);

            // 감사 영역 (빈 칸이면 위 행 값)
            String areaText = reader.value(r, header.col(AREA));
            if (areaText.isEmpty()) {
                areaText = prevAreaText == null ? "" : prevAreaText;
            }
            row.setAreaText(areaText);
            row.setAuditArea(AuditArea.fromLabel(areaText));
            if (areaText.isEmpty()) {
                row.addError("감사 영역이 비어 있습니다.");
            } else if (row.getAuditArea() == null) {
                row.addError("알 수 없는 감사 영역입니다: " + areaText);
            }

            // 감사 항목 (빈 칸이면 같은 영역의 위 행 값)
            String item = reader.value(r, header.col(ITEM));
            if (item.isEmpty() && prevItem != null && row.getAuditArea() == prevArea) {
                item = prevItem;
                row.setAuditItemInherited(true);
            }
            row.setAuditItem(item);
            if (item.isEmpty()) {
                row.addError("감사 항목이 비어 있습니다.");
            } else if (item.length() > 200) {
                row.addError("감사 항목은 200자 이하여야 합니다.");
            }

            if (detail.isEmpty()) {
                row.addError("항목 상세가 비어 있습니다.");
            } else if (detail.length() > 2000) {
                row.addError("항목 상세는 2000자 이하여야 합니다.");
            }

            row.setAuditPeriod(AuditPeriod.fromLabel(periodText));
            if (row.getAuditPeriod() == null) {
                row.addError(periodText.isEmpty() ? "감사 시기가 비어 있습니다."
                        : "감사 시기는 1분기·2분기·3분기·4분기·매분기 중 하나여야 합니다: " + periodText);
            }

            if (row.getRegulation().length() > 500) {
                row.addError("법규/규정은 500자 이하여야 합니다.");
            }

            rows.add(row);
            prevArea = row.getAuditArea();
            prevAreaText = areaText;
            prevItem = item.isEmpty() ? prevItem : item;
        }
        if (rows.isEmpty()) {
            preview.getFileErrors().add("헤더 아래에 항목이 없습니다.");
        }
    }

    private static Integer parseSeq(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            double d = Double.parseDouble(text);
            if (d < 1 || d > 9999 || d != Math.floor(d)) {
                return null;
            }
            return (int) d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Header(int rowIndex, Map<String, Integer> columns) {
        int col(String standard) {
            return columns.get(standard);
        }
    }

    /** 병합 셀을 고려해 셀 값을 문자열로 읽는다 */
    private static class SheetReader {

        final Sheet sheet;
        final DataFormatter formatter;
        final FormulaEvaluator evaluator;
        final List<CellRangeAddress> merged;

        SheetReader(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
            this.sheet = sheet;
            this.formatter = formatter;
            this.evaluator = evaluator;
            this.merged = sheet.getMergedRegions();
        }

        Header findHeader() {
            int last = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS);
            for (int r = sheet.getFirstRowNum(); r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, Integer> columns = new LinkedHashMap<>();
                for (Cell cell : row) {
                    String std = HEADER_ALIASES.get(normalize(formatter.formatCellValue(cell, evaluator)));
                    if (std != null) {
                        columns.putIfAbsent(std, cell.getColumnIndex());
                    }
                }
                if (columns.keySet().containsAll(List.of(SEQ, AREA, ITEM, DETAIL, PERIOD, REGULATION))) {
                    return new Header(r, columns);
                }
            }
            return null;
        }

        String value(int r, int c) {
            for (CellRangeAddress range : merged) {
                if (range.isInRange(r, c)) {
                    r = range.getFirstRow();
                    c = range.getFirstColumn();
                    break;
                }
            }
            Row row = sheet.getRow(r);
            Cell cell = row == null ? null : row.getCell(c);
            if (cell == null) {
                return "";
            }
            return formatter.formatCellValue(cell, evaluator).trim();
        }
    }
}
