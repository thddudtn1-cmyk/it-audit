package com.itaudit.export;

import java.util.List;
import java.util.Objects;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** 현행 엑셀 양식처럼 쓰기 위한 도우미: 제목·헤더·본문 서식, 셀 병합, 드롭다운 */
class SheetWriter {

    /** 원본 헤더 색 (#4472C4), 회신불필요 음영 (#E7E6E6) */
    private static final byte[] HEADER_FILL = {(byte) 0x44, (byte) 0x72, (byte) 0xC4};
    private static final byte[] GRAY_FILL = {(byte) 0xE7, (byte) 0xE6, (byte) 0xE6};

    final XSSFWorkbook wb;
    final Sheet sheet;
    private final CellStyle title;
    private final CellStyle header;
    private final CellStyle body;
    private final CellStyle bodyCenter;
    private final CellStyle gray;
    private final CellStyle grayCenter;
    private final CellStyle note;

    SheetWriter(XSSFWorkbook wb, String sheetName) {
        this.wb = wb;
        this.sheet = wb.createSheet(sheetName);
        Font bold = wb.createFont();
        bold.setBold(true);
        bold.setFontHeightInPoints((short) 12);
        title = wb.createCellStyle();
        title.setFont(bold);

        Font white = wb.createFont();
        white.setBold(true);
        white.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        header = bordered(fill(wb.createCellStyle(), HEADER_FILL));
        header.setFont(white);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);

        body = bordered(wb.createCellStyle());
        body.setWrapText(true);
        body.setVerticalAlignment(VerticalAlignment.TOP);
        bodyCenter = bordered(wb.createCellStyle());
        bodyCenter.cloneStyleFrom(body);
        bodyCenter.setAlignment(HorizontalAlignment.CENTER);
        gray = wb.createCellStyle();
        gray.cloneStyleFrom(body);
        fill(gray, GRAY_FILL);
        grayCenter = wb.createCellStyle();
        grayCenter.cloneStyleFrom(bodyCenter);
        fill(grayCenter, GRAY_FILL);
        note = wb.createCellStyle();
        note.setWrapText(false);
    }

    private static CellStyle fill(CellStyle style, byte[] rgb) {
        ((XSSFCellStyle) style).setFillForegroundColor(new XSSFColor(rgb, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle bordered(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    Row row(int r) {
        Row row = sheet.getRow(r);
        return row == null ? sheet.createRow(r) : row;
    }

    void title(int r, int c, String text) {
        Cell cell = row(r).createCell(c);
        cell.setCellValue(text);
        cell.setCellStyle(title);
    }

    void note(int r, int c, String text) {
        Cell cell = row(r).createCell(c);
        cell.setCellValue(text);
        cell.setCellStyle(note);
    }

    /** 회신불필요 범례용 회색 칸 */
    void grayBox(int r, int c) {
        row(r).createCell(c).setCellStyle(gray);
    }

    void header(int r, int firstCol, List<String> names, int[] widths) {
        Row row = row(r);
        row.setHeightInPoints(24);
        for (int i = 0; i < names.size(); i++) {
            Cell cell = row.createCell(firstCol + i);
            cell.setCellValue(names.get(i));
            cell.setCellStyle(header);
            sheet.setColumnWidth(firstCol + i, widths[i] * 256);
        }
        sheet.createFreezePane(0, r + 1);
    }

    void cell(int r, int c, Object value, boolean center, boolean grayed) {
        Cell cell = row(r).createCell(c);
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value != null) {
            cell.setCellValue(String.valueOf(value));
        }
        cell.setCellStyle(grayed ? (center ? grayCenter : gray) : (center ? bodyCenter : body));
    }

    /** 같은 값이 이어지는 칸을 병합 (원본처럼 감사 영역·감사 항목) */
    void mergeRuns(int col, int firstRow, List<?> values) {
        int start = 0;
        for (int i = 1; i <= values.size(); i++) {
            if (i == values.size() || !Objects.equals(values.get(i), values.get(start))) {
                merge(firstRow + start, firstRow + i - 1, col);
                start = i;
            }
        }
    }

    void merge(int firstRow, int lastRow, int col) {
        if (lastRow > firstRow) {
            sheet.addMergedRegion(new CellRangeAddress(firstRow, lastRow, col, col));
        }
    }

    /** 원본 ⑤ 처럼 목록 선택 칸 */
    void dropdown(int firstRow, int lastRow, int col, String... values) {
        if (lastRow < firstRow) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation dv = helper.createValidation(helper.createExplicitListConstraint(values),
                new CellRangeAddressList(firstRow, lastRow, col, col));
        dv.setShowErrorBox(true);
        sheet.addValidationData(dv);
    }
}
