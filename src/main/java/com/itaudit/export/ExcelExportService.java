package com.itaudit.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.itaudit.auth.LoginUser;
import com.itaudit.checklist.ChecklistItem;
import com.itaudit.checklist.ChecklistMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;
import com.itaudit.evidence.Evidence;
import com.itaudit.evidence.EvidenceOwnerType;
import com.itaudit.evidence.EvidenceService;
import com.itaudit.improvement.ActionResult;
import com.itaudit.improvement.Improvement;
import com.itaudit.improvement.ImprovementFilter;
import com.itaudit.improvement.ImprovementKind;
import com.itaudit.improvement.ImprovementService;
import com.itaudit.improvement.LinkedItem;
import com.itaudit.request.AuditRequest;
import com.itaudit.request.AuditRequestService;
import com.itaudit.round.AuditRound;
import com.itaudit.round.RoundItem;
import com.itaudit.round.RoundService;

/**
 * 엑셀 내보내기 (F-09): ②~⑤ 현행 양식의 시트명·제목 위치·헤더 이름·열 순서를 그대로 따른다.
 * 감사부서 등이 계속 엑셀로 주고받을 때를 위한 기능이라 표시값(코드가 아닌 한글 이름)으로 쓴다.
 */
@Service
public class ExcelExportService {

    /** 엑셀 파일 1개 */
    public record ExcelFile(String fileName, byte[] content) {
    }

    private final ChecklistMapper checklistMapper;
    private final RoundService roundService;
    private final AuditRequestService requestService;
    private final ImprovementService improvementService;
    private final EvidenceService evidenceService;
    private final ActivityLogService activityLog;

    public ExcelExportService(ChecklistMapper checklistMapper, RoundService roundService,
                              AuditRequestService requestService, ImprovementService improvementService,
                              EvidenceService evidenceService, ActivityLogService activityLog) {
        this.checklistMapper = checklistMapper;
        this.roundService = roundService;
        this.requestService = requestService;
        this.improvementService = improvementService;
        this.evidenceService = evidenceService;
        this.activityLog = activityLog;
    }

    // ---------- ② IT자체감사 체크리스트 ----------

    public ExcelFile checklist(int year, LoginUser user) {
        List<ChecklistItem> items = checklistMapper.findByYear(year, null, null);
        if (items.isEmpty()) {
            throw new BusinessException(year + "년 체크리스트가 없습니다.");
        }
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            SheetWriter w = new SheetWriter(wb, "IT자체감사 체크리스트");
            // 원본: 3행 B열부터 헤더
            w.header(2, 1, List.of("순번", "감사 영역", "감사 항목", "항목 상세", "감사 시기", "법규/규정"),
                    new int[]{6, 22, 30, 60, 10, 40});
            int r = 3;
            for (ChecklistItem i : items) {
                w.cell(r, 1, i.getSeqNo(), true, false);
                w.cell(r, 2, i.getAuditArea().getLabel(), false, false);
                w.cell(r, 3, i.getAuditItem(), false, false);
                w.cell(r, 4, i.getItemDetail(), false, false);
                w.cell(r, 5, i.getAuditPeriod().getLabel(), true, false);
                w.cell(r, 6, i.getRegulation(), false, false);
                r++;
            }
            w.mergeRuns(2, 3, items.stream().map(ChecklistItem::getAuditArea).toList());
            w.mergeRuns(3, 3, items.stream().map(i -> i.getAuditArea() + "|" + i.getAuditItem()).toList());
            return done(wb, "2_IT자체감사_체크리스트_" + year + ".xlsx", "체크리스트 " + year, user);
        } catch (IOException e) {
            throw new BusinessException("엑셀 파일을 만들지 못했습니다.");
        }
    }

    // ---------- ③ 분기 IT자체감사 총괄표 ----------

    public ExcelFile roundSheet(Long roundId, LoginUser user) {
        AuditRound round = roundService.getRound(roundId, user);
        List<RoundItem> items = roundService.findItems(roundId).stream().filter(i -> !i.isExcluded()).toList();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // 원본은 시트명이 회차와 맞지 않았다(복사 흔적) → 회차에서 자동 표기
            SheetWriter w = new SheetWriter(wb, round.getRoundYear() + "_" + round.getQuarter() + "Q");
            w.grayBox(1, 7);
            w.note(1, 8, ": 회신불필요");
            w.note(2, 10, "정상 : 법규/규정 준수 상태, 개선필요: 점검 결과 정상이나 일부 항목 중 개선이 필요상태(긴급x) ,"
                    + "미흡 : 법규/규정 위반은 아니나 개선이 필요 상태, 위험: 법규/규정 위반 상태");
            w.header(3, 1, List.of("No", "감사 영역", "평가항목", "법규/규정", "점검방식", "점검시기", "담당팀", "담당자",
                    "사전요청자료", "점검결과", "점검 내용 상세", "비고1", "조치내용 및 개선 예정일"),
                    new int[]{5, 22, 34, 26, 55, 9, 12, 15, 24, 11, 49, 12, 64});
            int r = 4;
            int no = 1;
            for (RoundItem i : items) {
                boolean g = i.isNoReply();
                w.cell(r, 1, no++, true, g);
                w.cell(r, 2, i.getAuditArea().getLabel(), false, g);
                w.cell(r, 3, i.getAuditItem(), false, g);
                w.cell(r, 4, i.getRegulation(), false, g);
                w.cell(r, 5, i.getItemDetail(), false, g);
                w.cell(r, 6, i.getAuditPeriod().getLabel(), true, g);
                w.cell(r, 7, i.getTeamName(), true, g);
                w.cell(r, 8, i.getAssigneeName(), true, g);
                w.cell(r, 9, material(i.getRequestMaterial(), i.getSampleSize()), false, g);
                w.cell(r, 10, i.getInspectionResult() == null ? null : i.getInspectionResult().getLabel(), true, g);
                w.cell(r, 11, i.getInspectionDetail(), false, g);
                w.cell(r, 12, i.getRemark1(), false, g);
                // 저장은 조치내용·개선 예정일로 나누고 ③ 양식에서는 한 칸으로 합친다 (PRD 9장)
                w.cell(r, 13, actionWithDate(i.getActionPlan(), i.getPlannedDate()), false, g);
                r++;
            }
            w.mergeRuns(2, 4, items.stream().map(RoundItem::getAuditArea).toList());
            return done(wb, "3_" + round.getRoundYear() + "_" + round.getQuarter() + "분기_IT자체감사_총괄표.xlsx",
                    "총괄표 " + round.getLabel(), user);
        } catch (IOException e) {
            throw new BusinessException("엑셀 파일을 만들지 못했습니다.");
        }
    }

    // ---------- ④ 적정성 점검 ----------
    // 프로세스 변경: ④ 는 감사부서가 제출된 분기 점검 내역을 항목별로 검토한 결과(보완자료 요청 포함)를 담는다.

    public ExcelFile requests(Long roundId, LoginUser user) {
        AuditRound round = requestService.getRound(roundId);
        List<AuditRequestService.Target> targets = requestService.findTargets(roundId);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            SheetWriter w = new SheetWriter(wb, round.getRoundYear() + "_" + round.getQuarter() + "Q");
            w.title(0, 1, "■ " + round.getRoundYear() + "년 " + round.getQuarter() + "분기 IT자체감사 적정성 점검");
            w.header(2, 1, List.of("No", "감사 영역", "평가항목", "점검방식", "점검결과", "보완 요청자료", "비고",
                    "적정성 판정", "미흡 내용·검토 의견"), new int[]{5, 22, 32, 50, 11, 40, 40, 16, 45});
            int r = 3;
            int no = 1;
            for (AuditRequestService.Target t : targets) {
                RoundItem i = t.item();
                AuditRequest q = t.review();
                w.cell(r, 1, no++, true, false);
                w.cell(r, 2, i.getAuditArea().getLabel(), false, false);
                w.cell(r, 3, i.getAuditItem(), false, false);
                w.cell(r, 4, i.getItemDetail(), false, false);
                w.cell(r, 5, i.getInspectionResult() == null ? null : i.getInspectionResult().getLabel(), true, false);
                w.cell(r, 6, q == null ? null : material(q.getRequestMaterial(), q.getSampleSize()), false, false);
                w.cell(r, 7, q == null ? null : q.getRemark(), false, false);
                w.cell(r, 8, t.state().getLabel() + (q != null && q.getConfirmedResult() != null
                        ? " (" + q.getConfirmedResult().getLabel() + ")" : ""), true, false);
                w.cell(r, 9, joinLines(q == null ? null : q.getFindingDetail(), q == null ? null : q.getDeptComment()), false, false);
                r++;
            }
            w.mergeRuns(2, 3, targets.stream().map(t -> t.item().getAuditArea()).toList());
            return done(wb, "4_" + round.getRoundYear() + "_" + round.getQuarter() + "분기_적정성점검.xlsx",
                    "적정성 점검 " + round.getLabel(), user);
        } catch (IOException e) {
            throw new BusinessException("엑셀 파일을 만들지 못했습니다.");
        }
    }

    private static String joinLines(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + "\n" + b;
    }

    // ---------- ⑤ IT자체감사 개선사항 관리대장 (시트 2개) ----------

    public ExcelFile improvements(LoginUser user) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            writeLedger(new SheetWriter(wb, "개선사항 관리대장"), ImprovementKind.IMPROVEMENT, user);
            writeLedger(new SheetWriter(wb, "권고사항"), ImprovementKind.RECOMMENDATION, user);
            return done(wb, "5_IT자체감사_개선사항_관리대장.xlsx", "개선사항 관리대장", user);
        } catch (IOException e) {
            throw new BusinessException("엑셀 파일을 만들지 못했습니다.");
        }
    }

    /** 개선 1건이 여러 항목에 걸치면 항목마다 행을 두고 개선 단위 칸은 병합 (원본 J12:N14 처럼) */
    private void writeLedger(SheetWriter w, ImprovementKind kind, LoginUser user) {
        boolean rec = kind == ImprovementKind.RECOMMENDATION;
        w.note(1, 1, "◎");
        w.title(1, 2, rec ? "IT자체감사 권고사항" : "IT자체감사 개선사항 관리대장");
        List<String> head = new ArrayList<>(List.of("No", "감사 영역", "감사 항목", "항목 상세", "감사 시기", "담당부서/담당자"));
        head.addAll(rec ? List.of("상세 내용") : List.of("점검 결과", "점검 내용 상세"));
        head.addAll(List.of("개선 방안", "개선예정일", "조치 결과", "조치 현황", "증빙자료"));
        w.header(3, 1, head, rec ? new int[]{5, 22, 30, 50, 10, 20, 45, 40, 12, 10, 40, 30}
                : new int[]{5, 22, 30, 50, 10, 20, 10, 45, 40, 12, 10, 40, 30});

        ImprovementFilter f = new ImprovementFilter();
        f.setKind(kind);
        f.setClosed(ImprovementFilter.Closed.ALL);
        List<Improvement> list = improvementService.findList(f, user);
        int resultCol = 7;
        int actionCol = rec ? 10 : 11;
        int r = 4;
        int no = 1;
        for (Improvement m : list) {
            List<LinkedItem> items = m.getItems().isEmpty() ? List.of(new LinkedItem()) : m.getItems();
            int first = r;
            for (LinkedItem x : items) {
                w.cell(r, 1, no, true, false);
                w.cell(r, 2, x.getAuditArea() == null ? null : x.getAuditArea().getLabel(), false, false);
                w.cell(r, 3, x.getAuditItem(), false, false);
                w.cell(r, 4, x.getItemDetail(), false, false);
                w.cell(r, 5, m.getRoundShortLabel(), true, false);
                w.cell(r, 6, m.getOwnerLabel(), true, false);
                int c = 7;
                if (!rec) {
                    w.cell(r, c++, m.getInspectionResult() == null ? null : m.getInspectionResult().getLabel(), true, false);
                }
                w.cell(r, c++, m.getDetail(), false, false);
                w.cell(r, c++, m.getImprovementPlan(), false, false);
                w.cell(r, c++, m.getPlannedDate() == null ? null : m.getPlannedDate().toString(), true, false);
                w.cell(r, c++, m.getActionResult().getLabel() + (m.isClosed() ? " (최종확정)" : m.isReviewPending() ? " (검토 대기)" : ""), true, false);
                w.cell(r, c++, m.getActionStatus(), false, false);
                w.cell(r, c, evidenceNames(m), false, false);
                r++;
            }
            // 개선 단위 칸 병합: No, 감사 시기 이후 전부
            w.merge(first, r - 1, 1);
            for (int c = 5; c <= (rec ? 12 : 13); c++) {
                w.merge(first, r - 1, c);
            }
            no++;
        }
        if (!rec) {
            w.dropdown(4, Math.max(4, r - 1), resultCol, "정상", "개선필요", "미흡", "위험");
        }
        w.dropdown(4, Math.max(4, r - 1), actionCol, java.util.Arrays.stream(ActionResult.values())
                .map(ActionResult::getLabel).toArray(String[]::new));
    }

    private String evidenceNames(Improvement m) {
        List<Evidence> files = evidenceService.findActive(EvidenceOwnerType.IMPROVEMENT, m.getImprovementId());
        return files.isEmpty() ? null : files.stream().map(Evidence::getOriginalName).collect(Collectors.joining("\n"));
    }

    // ---------- 공통 ----------

    private static String material(String material, Integer sample) {
        if (sample == null) {
            return material;
        }
        return (material == null ? "" : material + " ") + "(표본 " + sample + "개)";
    }

    private static String actionWithDate(String plan, java.time.LocalDate date) {
        if (plan == null && date == null) {
            return null;
        }
        return (plan == null ? "" : plan) + (date == null ? "" : (plan == null ? "" : "\n") + "(개선 예정일 " + date + ")");
    }

    private ExcelFile done(XSSFWorkbook wb, String fileName, String what, LoginUser user) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        activityLog.log(ActivityLogService.EXCEL_EXPORT, "EXPORT", null, what + " → " + fileName);
        return new ExcelFile(fileName, out.toByteArray());
    }
}
