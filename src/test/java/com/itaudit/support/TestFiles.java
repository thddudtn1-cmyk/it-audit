package com.itaudit.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 테스트에서 docs/ 아래 원본 파일을 읽는다 (Maven 실행 위치 = 프로젝트 루트) */
public final class TestFiles {

    public static final Path CHECKLIST_XLSX = Path.of("docs/reference/2_IT자체감사_체크리스트.xlsx");
    public static final Path CHECKLIST_SEED_CSV = Path.of("docs/checklist_seed.csv");

    private TestFiles() {
    }

    public static byte[] bytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    /** 따옴표 안 줄바꿈·쉼표를 처리하는 간단한 CSV 파서 (BOM 제거) */
    public static List<List<String>> readCsv(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
