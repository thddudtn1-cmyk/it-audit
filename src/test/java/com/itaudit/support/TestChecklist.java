package com.itaudit.support;

import java.io.InputStream;
import java.nio.file.Files;

import org.springframework.stereotype.Component;

import com.itaudit.auth.LoginUser;
import com.itaudit.checklist.ChecklistExcelParser;
import com.itaudit.checklist.ChecklistService;
import com.itaudit.checklist.ChecklistUploadPreview;

/** ② 원본 엑셀로 체크리스트를 등록하는 테스트 도우미 */
@Component
public class TestChecklist {

    private final ChecklistExcelParser parser;
    private final ChecklistService service;

    public TestChecklist(ChecklistExcelParser parser, ChecklistService service) {
        this.parser = parser;
        this.service = service;
    }

    public void load(int year, LoginUser actor) throws Exception {
        try (InputStream in = Files.newInputStream(TestFiles.CHECKLIST_XLSX)) {
            ChecklistUploadPreview preview = parser.parse(in, "②.xlsx", year);
            service.saveUpload(preview, actor);
        }
    }
}
