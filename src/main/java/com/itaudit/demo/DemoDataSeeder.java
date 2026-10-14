package com.itaudit.demo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.itaudit.admin.Team;
import com.itaudit.admin.TeamMapper;
import com.itaudit.auth.AppUser;
import com.itaudit.auth.AppUserMapper;
import com.itaudit.auth.DemoAccounts;
import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.checklist.AuditArea;
import com.itaudit.checklist.AuditPeriod;
import com.itaudit.checklist.ChecklistItem;
import com.itaudit.checklist.ChecklistMapper;
import com.itaudit.comment.CommentService;
import com.itaudit.comment.CommentTarget;
import com.itaudit.improvement.ActionResult;
import com.itaudit.improvement.Improvement;
import com.itaudit.improvement.ImprovementForm;
import com.itaudit.improvement.ImprovementKind;
import com.itaudit.improvement.ImprovementMapper;
import com.itaudit.improvement.ImprovementService;
import com.itaudit.inspection.InspectionForm;
import com.itaudit.inspection.InspectionResult;
import com.itaudit.inspection.InspectionService;
import com.itaudit.request.AuditRequest;
import com.itaudit.request.AuditRequestMapper;
import com.itaudit.request.AuditRequestService;
import com.itaudit.request.RequestReviewForm;
import com.itaudit.round.AssignmentForm;
import com.itaudit.round.ReviewService;
import com.itaudit.round.RoundCreateForm;
import com.itaudit.round.RoundItem;
import com.itaudit.round.RoundMapper;
import com.itaudit.round.RoundService;

/**
 * 시연용 데이터 생성. 앱이 뜰 때 분기 점검 회차가 하나도 없으면 한 번만 실행한다 (회차가 있으면 건드리지 않는다).
 * 이미 올라온 체크리스트가 있으면 그것을 그대로 쓰고(올해 항목이 있으면 올해, 없으면 가장 최근 연도),
 * 체크리스트가 없을 때만 내장 시드(② 체크리스트 43건)를 올린다.
 * Render 데모처럼 DB 가 재배포마다 초기화되는 환경에서 매번 같은 시연 데이터가 채워지게 하려는 용도다.
 *
 * 화면에서 쓰는 서비스(회차 생성·배정·점검 입력·검토·적정성 점검·개선사항·코멘트)를 그대로 호출해 만들기 때문에
 * 실제 업무 규칙을 통과한 데이터다. 전체를 한 트랜잭션으로 처리해 실패하면 아무것도 남기지 않는다.
 *
 * 켜는 조건: app.demo-data.enabled=true (환경변수 DEMO_DATA_ENABLED) 이고 데모 로그인(app.demo-login.enabled)도 켜져 있어야 한다
 * (주관자·감사부서·담당자 데모 계정을 행위자로 쓰기 때문). 금액·개인정보가 아닌 가상의 점검 내용만 넣는다.
 *
 * 만들어지는 상태 (올해 기준 1~3분기):
 *  - 1분기: 적정성 점검까지 끝난 완료 회차 (개선사항 1건 최종확정, 1건 진행중·예정일 경과)
 *  - 2분기: 감사부서 제출 후 적정성 점검 진행 중 (미검토·보완 요청·보완 제출·적정·미흡 확정),
 *           개선사항 4건 (진행중·예정일 임박 / 반려됨 / 완료 검토 대기·예정일 경과 / 예정), 권고사항 1건
 *  - 3분기: 작성중 (제출·확인·반려·임시저장·미착수 혼재, 항목 1건 제외)
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** 팀 코드 */
    private static final String INFRA = "INFRA";
    private static final String SEC = "SEC";
    private static final String DEV = "DEV";
    private static final String DBA = "DBA";

    private final PlatformTransactionManager txManager;
    private final AppUserMapper userMapper;
    private final TeamMapper teamMapper;
    private final ChecklistMapper checklistMapper;
    private final RoundMapper roundMapper;
    private final RoundService roundService;
    private final ReviewService reviewService;
    private final InspectionService inspectionService;
    private final AuditRequestService requestService;
    private final AuditRequestMapper requestMapper;
    private final ImprovementService improvementService;
    private final ImprovementMapper improvementMapper;
    private final CommentService commentService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(PlatformTransactionManager txManager, AppUserMapper userMapper, TeamMapper teamMapper,
                          ChecklistMapper checklistMapper, RoundMapper roundMapper, RoundService roundService,
                          ReviewService reviewService, InspectionService inspectionService,
                          AuditRequestService requestService, AuditRequestMapper requestMapper,
                          ImprovementService improvementService, ImprovementMapper improvementMapper,
                          CommentService commentService, PasswordEncoder passwordEncoder) {
        this.txManager = txManager;
        this.userMapper = userMapper;
        this.teamMapper = teamMapper;
        this.checklistMapper = checklistMapper;
        this.roundMapper = roundMapper;
        this.roundService = roundService;
        this.reviewService = reviewService;
        this.inspectionService = inspectionService;
        this.requestService = requestService;
        this.requestMapper = requestMapper;
        this.improvementService = improvementService;
        this.improvementMapper = improvementMapper;
        this.commentService = commentService;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- 실행 ----------

    @Override
    public void run(ApplicationArguments args) {
        if (!roundMapper.findRounds(null).isEmpty()) {
            log.info("이미 분기 점검 회차가 있어 시연용 데이터를 만들지 않습니다.");
            return;
        }
        AppUser lead = userMapper.findByLoginId(DemoAccounts.of(Role.AUDIT_LEAD).getLoginId());
        AppUser dept = userMapper.findByLoginId(DemoAccounts.of(Role.AUDIT_DEPT).getLoginId());
        AppUser demoInspector = userMapper.findByLoginId(DemoAccounts.of(Role.INSPECTOR).getLoginId());
        if (lead == null || dept == null || demoInspector == null) {
            log.warn("데모 계정이 없어 시연용 데이터를 만들지 않습니다 (DEMO_LOGIN_ENABLED=true 필요).");
            return;
        }
        try {
            new TransactionTemplate(txManager).executeWithoutResult(status -> seed(lead, dept, demoInspector));
            log.info("시연용 데이터를 만들었습니다 (회차 3개).");
        } catch (RuntimeException e) {
            log.error("시연용 데이터 생성에 실패해 모두 되돌렸습니다.", e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** 사용자 */
    private record Actors(LoginUser lead, LoginUser dept, LoginUser kim, LoginUser lee, LoginUser park, LoginUser choi) {
        LoginUser inspectorOf(String team) {
            return switch (team) {
                case INFRA -> kim;
                case SEC -> lee;
                case DEV -> park;
                default -> choi;
            };
        }
    }

    /** 점검 입력 1건 */
    private record Finding(InspectionResult result, String detail, String plan, int plannedOffsetDays) {
    }

    private void seed(AppUser leadUser, AppUser deptUser, AppUser demoInspector) {
        LocalDate today = LocalDate.now();
        List<Integer> years = checklistMapper.findYears();
        boolean hasChecklist = !years.isEmpty();
        int year = !hasChecklist || years.contains(today.getYear()) ? today.getYear() : years.get(0);

        if (hasChecklist) {
            // 이미 올라온 체크리스트를 쓴다. 시연 시나리오가 기대하는 순번·감사 시기와 맞는지 먼저 확인한다
            String problem = checklistMismatch(year);
            if (problem != null) {
                log.warn("{}년 체크리스트가 시연 시나리오와 맞지 않아 시연용 데이터를 만들지 않습니다: {}", year, problem);
                return;
            }
        }
        Actors a = createPeople(leadUser, deptUser, demoInspector);
        if (!hasChecklist) {
            loadChecklist(year, a.lead());
        }

        seedRound1(year, today, a);
        seedRound2(year, today, a);
        seedRound3(year, a);
    }

    /** 시나리오가 순번으로 가리키는 항목의 감사 시기 (② 체크리스트 기준) */
    private static final Map<Integer, AuditPeriod> EXPECTED_PERIODS = Map.ofEntries(
            Map.entry(1, AuditPeriod.Q1), Map.entry(4, AuditPeriod.Q1), Map.entry(6, AuditPeriod.Q1),
            Map.entry(7, AuditPeriod.EVERY), Map.entry(8, AuditPeriod.Q2), Map.entry(11, AuditPeriod.Q2),
            Map.entry(14, AuditPeriod.Q2), Map.entry(16, AuditPeriod.Q2), Map.entry(19, AuditPeriod.Q2),
            Map.entry(21, AuditPeriod.EVERY), Map.entry(22, AuditPeriod.EVERY), Map.entry(23, AuditPeriod.Q3),
            Map.entry(24, AuditPeriod.Q3), Map.entry(25, AuditPeriod.Q3), Map.entry(26, AuditPeriod.Q3),
            Map.entry(34, AuditPeriod.EVERY), Map.entry(42, AuditPeriod.EVERY));

    /** 시나리오와 맞지 않으면 이유, 맞으면 null */
    private String checklistMismatch(int year) {
        for (Map.Entry<Integer, AuditPeriod> e : EXPECTED_PERIODS.entrySet()) {
            ChecklistItem item = checklistMapper.findBySeqNo(year, e.getKey());
            if (item == null || !item.isActive() || item.getAuditPeriod() != e.getValue()) {
                return "순번 " + e.getKey() + " 항목이 없거나 감사 시기가 " + e.getValue().getLabel() + "이(가) 아닙니다";
            }
        }
        return null;
    }

    // ---------- 팀·사용자 ----------

    private Actors createPeople(AppUser leadUser, AppUser deptUser, AppUser demoInspector) {
        team(INFRA, "인프라운영팀", 1);
        team(SEC, "정보보호팀", 2);
        team(DEV, "응용개발팀", 3);
        team(DBA, "DB운영팀", 4);

        // 역할 카드의 '점검 담당자'(데모 담당자)는 인프라운영팀 소속으로 옮겨 배정 항목이 보이게 한다
        demoInspector.setTeamCode(INFRA);
        demoInspector.setUserName("데모 담당자");
        userMapper.update(demoInspector);

        AppUser lee = inspector("demo-lee", "이보안", SEC);
        AppUser park = inspector("demo-park", "박개발", DEV);
        AppUser choi = inspector("demo-choi", "최디비", DBA);
        return new Actors(new LoginUser(leadUser), new LoginUser(deptUser), new LoginUser(userMapper.findById(demoInspector.getUserId())),
                new LoginUser(lee), new LoginUser(park), new LoginUser(choi));
    }

    private void team(String code, String name, int order) {
        Team t = new Team();
        t.setTeamCode(code);
        t.setTeamName(name);
        t.setSortOrder(order);
        t.setUseYn("Y");
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        teamMapper.insert(t);
    }

    /** 데모 담당자 외의 시연용 담당자. 비밀번호는 무작위라 아이디 로그인은 할 수 없다 (역할 카드는 데모 담당자만 사용) */
    private AppUser inspector(String loginId, String name, String teamCode) {
        AppUser u = new AppUser();
        u.setLoginId(loginId);
        u.setUserName(name);
        u.setTeamCode(teamCode);
        u.setRoleCode(Role.INSPECTOR);
        u.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        u.setPwChangeRequired("N");
        u.setUseYn("Y");
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(u);
        return userMapper.findById(u.getUserId());
    }

    // ---------- 체크리스트 (② 체크리스트 43건) ----------

    private void loadChecklist(int year, LoginUser actor) {
        List<List<String>> rows = readCsv("/demo/checklist_seed.csv");
        String prevItem = null;
        LocalDateTime now = LocalDateTime.now();
        for (List<String> r : rows.subList(1, rows.size())) {
            if (r.size() < 6 || r.get(0).isBlank()) {
                continue;
            }
            // ② 원본은 #34~#40 의 감사 항목 칸이 비어 있어 위 행 값을 이어받는다 (엑셀 업로드와 같은 규칙)
            String auditItem = r.get(2).isBlank() ? prevItem : r.get(2).trim();
            prevItem = auditItem;
            ChecklistItem item = new ChecklistItem();
            item.setApplyYear(year);
            item.setSeqNo(Integer.parseInt(r.get(0).trim()));
            item.setAuditArea(AuditArea.fromLabel(r.get(1)));
            item.setAuditItem(auditItem);
            item.setItemDetail(r.get(3).trim());
            item.setAuditPeriod(AuditPeriod.fromLabel(r.get(4)));
            item.setRegulation(r.get(5).isBlank() ? null : r.get(5).trim());
            item.setUseYn("Y");
            item.setCreatedBy(actor.getUserId());
            item.setCreatedAt(now);
            item.setUpdatedBy(actor.getUserId());
            item.setUpdatedAt(now);
            checklistMapper.insert(item);
        }
    }

    // ---------- 1분기: 적정성 점검까지 끝난 완료 회차 ----------

    private void seedRound1(int year, LocalDate today, Actors a) {
        Long round = createRound(year, 1, Set.of(), a);
        assign(round, Set.of(6), a);   // #6 은 회신불필요

        Map<Integer, Finding> findings = Map.of(
                4, new Finding(InspectionResult.NEEDS_IMPROVEMENT, "사고 영향도·심각도 평가 기준표에 심각도 구분이 일부 누락됨",
                        "영향도·심각도 평가 기준표 개정", -70),
                7, new Finding(InspectionResult.INSUFFICIENT, "운영 적용 전 책임자 승인이 누락된 변경 건 2건 확인",
                        "운영 적용 승인 절차 전산화", -20));
        inspectAll(round, findings, a);
        reviewService.confirmAll(round, a.lead());
        reviewService.submitToAuditDept(round, a.lead());   // #4 #7 이 개선사항으로 자동 등록된다

        // 적정성 점검: 모두 적정으로 판정하고 완료
        as(a.dept());
        for (RoundItem item : roundMapper.findItems(round)) {
            if (isTarget(item)) {
                requestService.review(item.getRoundItemId(), decision(RequestReviewForm.Decision.NO_FINDING), a.dept());
            }
        }
        requestService.completeRound(round, a.dept());

        // 개선사항: #4 는 조치 완료 후 주관자가 최종확정, #7 은 진행중 (예정일 경과)
        Map<Integer, RoundItem> bySeq = bySeq(round);
        Long imp4 = improvementOf(bySeq.get(4));
        act(imp4, a.kim(), ActionResult.DONE, "평가 기준표 개정 및 전 담당자 교육 완료", null);
        as(a.lead());
        improvementService.close(imp4, "개정 기준표와 교육 자료 확인", a.lead());
        Long imp7 = improvementOf(bySeq.get(7));
        act(imp7, a.park(), ActionResult.IN_PROGRESS, "승인 절차 전산화 개발 진행 중 (테스트 단계)", null);
    }

    // ---------- 2분기: 감사부서 제출 후 적정성 점검 진행 중 ----------

    private void seedRound2(int year, LocalDate today, Actors a) {
        Long round = createRound(year, 2, Set.of(), a);
        assign(round, Set.of(16), a);   // #16 은 회신불필요

        Map<Integer, Finding> findings = Map.of(
                11, new Finding(InspectionResult.INSUFFICIENT, "비상대응훈련 결과의 금융위원회 보고 증적이 일부 누락됨",
                        "훈련 결과 보고 절차 정비 및 증적 보관", 5),
                14, new Finding(InspectionResult.NEEDS_IMPROVEMENT, "자원별 임계치 기준 중 일부 서비스가 누락됨",
                        "전 서비스 임계치 기준 수립", 40),
                19, new Finding(InspectionResult.RISK, "일괄작업 수행자 행위에 대한 책임자 모니터링 기록이 없음 (규정 위반)",
                        "모니터링 서식 도입 및 소급 작성", -5),
                34, new Finding(InspectionResult.NEEDS_IMPROVEMENT, "프로그램 변경 내용의 제3자 검증이 분기 말에 몰려 수행됨",
                        "제3자 검증 주기를 월 단위로 조정", 25));
        inspectAll(round, findings, a);
        reviewService.confirmAll(round, a.lead());
        reviewService.submitToAuditDept(round, a.lead());   // #11 #14 #19 #34 자동 등록

        Map<Integer, RoundItem> bySeq = bySeq(round);

        // --- 적정성 점검: 일부만 검토한 상태 ---
        as(a.dept());
        for (int seq : List.of(7, 8, 9, 13, 21)) {
            requestService.review(bySeq.get(seq).getRoundItemId(), decision(RequestReviewForm.Decision.NO_FINDING), a.dept());
        }
        RequestReviewForm finding = decision(RequestReviewForm.Decision.FINDING);
        finding.setConfirmedResult(InspectionResult.INSUFFICIENT);
        finding.setFindingDetail("비상대응훈련 결과 보고 증적 미비로 미흡 확정");
        requestService.review(bySeq.get(11).getRoundItemId(), finding, a.dept());

        // #10: 보완자료 요청 후 담당자 제출 대기
        RequestReviewForm waiting = decision(RequestReviewForm.Decision.SUPPLEMENT);
        waiting.setRequestMaterial("백업자료 복구테스트 증적 (최근 3개월)");
        waiting.setDeptComment("복구 테스트 결과 원본 화면이 필요합니다.");
        requestService.review(bySeq.get(10).getRoundItemId(), waiting, a.dept());

        // #12: 보완자료 요청 → 담당자 제출 → 재검토 대기
        RequestReviewForm supplement = decision(RequestReviewForm.Decision.SUPPLEMENT);
        supplement.setRequestMaterial("비상대응훈련 결과 금융위원회 보고 증적");
        requestService.review(bySeq.get(12).getRoundItemId(), supplement, a.dept());
        AuditRequest r12 = requestMapper.findByRoundItem(bySeq.get(12).getRoundItemId());
        as(a.kim());
        requestService.saveSubmission(r12.getRequestId(), "26년 4분기 내 보고 체계 보완 예정",
                List.of(file("비상대응훈련_결과보고.txt", "[시연용] 비상대응훈련 결과 및 금융위원회 보고 내역")), true, a.kim());

        // --- 개선사항 4건 ---
        Long imp11 = improvementOf(bySeq.get(11));   // 진행중, 예정일 임박
        act(imp11, a.kim(), ActionResult.IN_PROGRESS, "훈련 결과 보고 절차서 초안 작성 완료, 부서 협의 중", null);

        Long imp14 = improvementOf(bySeq.get(14));   // 완료 → 반려됨
        act(imp14, a.kim(), ActionResult.DONE, "임계치 기준 전체 시스템 반영 완료", null);
        as(a.lead());
        improvementService.sendBack(imp14, ActionResult.IN_PROGRESS, "시스템별 임계치 목록 전체를 증적으로 첨부해 주세요.", a.lead());

        Long imp19 = improvementOf(bySeq.get(19));   // 완료 검토 대기, 예정일 경과 + 코멘트·증빙
        commentService.add(CommentTarget.IMPROVEMENT, imp19, "예정일이 지났습니다. 조치 현황과 증빙을 확인해 주세요.", a.lead());
        act(imp19, a.park(), ActionResult.DONE, "일괄작업 모니터링 서식 도입, 지난 3개월분 소급 작성 완료",
                List.of(file("일괄작업_모니터링_기록.txt", "[시연용] 일괄작업 수행자 모니터링 기록 (소급 작성분)")));
        commentService.add(CommentTarget.IMPROVEMENT, imp19, "소급 작성본과 증빙 첨부했습니다. 검토 부탁드립니다.", a.park());
        // #34 는 예정 상태로 둔다

        // --- 권고사항 1건 (감사부서) ---
        as(a.dept());
        ImprovementForm rec = new ImprovementForm();
        rec.setKind(ImprovementKind.RECOMMENDATION);
        rec.setRoundId(round);
        rec.setRoundItemIds(List.of(bySeq.get(13).getRoundItemId()));
        rec.setTeamCode(INFRA);
        rec.setAssigneeId(a.kim().getUserId());
        rec.setDetail("성능관리 보고서를 분기 단위에서 월 단위로 작성하도록 권고합니다.");
        rec.setImprovementPlan("월간 성능관리 보고 체계 수립");
        rec.setPlannedDate(today.plusDays(60));
        improvementService.createRecommendation(rec, a.dept());
    }

    // ---------- 3분기: 작성중 ----------

    private void seedRound3(int year, Actors a) {
        Long round = createRound(year, 3, Set.of(25), a);   // #25 는 그룹 IT감사로 대체되어 제외
        assign(round, Set.of(), a);
        Map<Integer, RoundItem> bySeq = bySeq(round);

        submit(bySeq.get(7), normal(), a.park(), true);
        submit(bySeq.get(21), normal(), a.choi(), true);
        reviewService.confirm(bySeq.get(7).getRoundItemId(), a.lead());
        reviewService.confirm(bySeq.get(21).getRoundItemId(), a.lead());

        submit(bySeq.get(22), new Finding(InspectionResult.NEEDS_IMPROVEMENT, "운영 DB와 형상 DB의 저장 프로시저 2건 불일치",
                "불일치 프로시저 동기화 및 정기 대사 절차 수립", 50), a.choi(), true);
        submit(bySeq.get(23), normal(), a.lee(), true);

        // #24: 제출 → 주관자 반려 (코멘트로 의견 교환)
        submit(bySeq.get(24), normal(), a.lee(), true);
        reviewService.reject(bySeq.get(24).getRoundItemId(), "증적 캡처가 흐립니다. 선명한 증적으로 다시 첨부해 주세요.", a.lead());
        commentService.add(CommentTarget.ROUND_ITEM, bySeq.get(24).getRoundItemId(), "권한 모니터링 증빙을 보완해 주세요.", a.lead());
        commentService.add(CommentTarget.ROUND_ITEM, bySeq.get(24).getRoundItemId(), "확인 후 다시 제출하겠습니다.", a.lee());

        // #26: 임시저장만 한 상태
        submit(bySeq.get(26), new Finding(InspectionResult.INSUFFICIENT, "반출 승인 대장에 승인자 서명이 누락된 건 확인 중", null, 0),
                a.lee(), false);
        // 나머지 항목은 아직 입력 전
    }

    // ---------- 공통 동작 ----------

    private Long createRound(int year, int quarter, Set<Integer> excludedSeqs, Actors a) {
        as(a.lead());
        RoundCreateForm form = new RoundCreateForm();
        form.setYear(year);
        form.setQuarter(quarter);
        form.setCopyPrevious(false);
        for (int seq : excludedSeqs) {
            RoundCreateForm.Row row = new RoundCreateForm.Row();
            row.setChecklistItemId(checklistMapper.findBySeqNo(year, seq).getItemId());
            row.setExcluded(true);
            row.setExcludeReason("그룹 IT감사로 대체");
            form.getRows().add(row);
        }
        return roundService.create(form, a.lead());
    }

    /** 순번별 담당팀: 사고·백업·성능·유지보수 → 인프라, 테스트·일괄작업·개발변경 → 개발, DB·데이터변경 → DB, 정보보호 → 보안 */
    private static String teamOf(int seq) {
        if (seq <= 5) {
            return INFRA;
        }
        if (seq <= 7) {
            return DEV;
        }
        if (seq <= 17) {
            return INFRA;
        }
        if (seq <= 20) {
            return DEV;
        }
        if (seq <= 22) {
            return DBA;
        }
        if (seq <= 32) {
            return SEC;
        }
        return seq >= 42 ? DBA : DEV;
    }

    private static final Map<Integer, String> MATERIALS = Map.ofEntries(
            Map.entry(7, "ICMS 시스템변경 랜덤 5개"), Map.entry(8, "백업운영관리 매뉴얼"),
            Map.entry(10, "백업자료 복구테스트 증적"), Map.entry(12, "비상대응훈련 결과 금융위 보고 증적"),
            Map.entry(17, "예방점검 결과 후속 조치내역"), Map.entry(21, "불필요 테이블 점검·삭제 내역"),
            Map.entry(23, "인사이동자 접근권한 변경·말소 내역"), Map.entry(24, "권한 모니터링 증빙"),
            Map.entry(26, "반출입 통제 승인 자료"), Map.entry(28, "비인가 무선접속장비(AP) 점검 결과"),
            Map.entry(42, "데이터변경 요청서 랜덤 3개"));
    private static final Map<Integer, Integer> SAMPLES = Map.of(7, 5, 42, 3);

    /** 제외하지 않은 모든 항목에 담당팀·담당자·사전요청자료를 배정하고, 지정한 순번은 회신불필요로 표시 */
    private void assign(Long roundId, Set<Integer> noReplySeqs, Actors a) {
        as(a.lead());
        AssignmentForm form = new AssignmentForm();
        for (RoundItem item : roundMapper.findItems(roundId)) {
            AssignmentForm.Row row = AssignmentForm.Row.of(item);
            if (!item.isExcluded()) {
                String team = teamOf(item.getSeqNo());
                row.setTeamCode(team);
                row.setAssigneeId(a.inspectorOf(team).getUserId());
                row.setRequestMaterial(MATERIALS.get(item.getSeqNo()));
                row.setSampleSize(SAMPLES.get(item.getSeqNo()));
                row.setNoReply(noReplySeqs.contains(item.getSeqNo()));
            }
            form.getRows().add(row);
        }
        roundService.saveAssignment(roundId, form, a.lead());
    }

    private static boolean isTarget(RoundItem item) {
        return !item.isExcluded() && !item.isNoReply();
    }

    private static Finding normal() {
        return new Finding(InspectionResult.NORMAL, "관련 규정과 운영 현황을 점검한 결과 기준을 준수하고 있어 이상 없음", null, 0);
    }

    /** 제출 대상 항목 전체를 담당자가 입력·제출 (findings 에 없는 순번은 정상) */
    private void inspectAll(Long roundId, Map<Integer, Finding> findings, Actors a) {
        for (RoundItem item : roundMapper.findItems(roundId)) {
            if (isTarget(item)) {
                Finding f = findings.getOrDefault(item.getSeqNo(), normal());
                List<MultipartFile> files = item.getSeqNo() == 11
                        ? List.of(file("비상대응훈련_보고현황.txt", "[시연용] 비상대응훈련 결과 보고 현황 (일부 증적 누락)")) : List.of();
                submit(item, f, a.inspectorOf(item.getTeamCode()), true, files);
            }
        }
    }

    private void submit(RoundItem item, Finding f, LoginUser inspector, boolean submit) {
        submit(item, f, inspector, submit, List.of());
    }

    private void submit(RoundItem item, Finding f, LoginUser inspector, boolean submit, List<MultipartFile> files) {
        as(inspector);
        InspectionForm form = new InspectionForm();
        form.setInspectionResult(f.result());
        form.setInspectionDetail(f.detail());
        form.setActionPlan(f.plan());
        form.setPlannedDate(f.plan() == null ? null : LocalDate.now().plusDays(f.plannedOffsetDays()));
        inspectionService.save(item.getRoundItemId(), form, files, submit, inspector);
    }

    private RequestReviewForm decision(RequestReviewForm.Decision d) {
        RequestReviewForm f = new RequestReviewForm();
        f.setDecision(d);
        return f;
    }

    private Map<Integer, RoundItem> bySeq(Long roundId) {
        Map<Integer, RoundItem> map = new HashMap<>();
        for (RoundItem item : roundMapper.findItems(roundId)) {
            map.put(item.getSeqNo(), item);
        }
        return map;
    }

    /** 점검 제출 시 자동 등록된 개선사항 ID */
    private Long improvementOf(RoundItem item) {
        return improvementMapper.findIdsByRoundItem(item.getRoundItemId(), ImprovementKind.IMPROVEMENT).get(0);
    }

    /** 담당자의 조치 갱신 (개선 방안·개선예정일은 등록값 유지) */
    private void act(Long improvementId, LoginUser actor, ActionResult result, String status, List<MultipartFile> files) {
        as(actor);
        Improvement m = improvementService.get(improvementId, actor);
        ImprovementForm form = new ImprovementForm();
        form.setImprovementPlan(m.getImprovementPlan());
        form.setPlannedDate(m.getPlannedDate());
        form.setActionResult(result);
        form.setActionStatus(status);
        improvementService.update(improvementId, form, files, actor);
    }

    /** 서비스가 로그 행위자를 현재 인증 정보에서 읽으므로 단계마다 행위자를 맞춘다 */
    private void as(LoginUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    // ---------- 보조: CSV·가상 첨부파일 ----------

    /** 따옴표 안 쉼표·줄바꿈을 처리하는 간단한 CSV 파서 (BOM 제거) */
    private static List<List<String>> readCsv(String resource) {
        try (InputStream in = DemoDataSeeder.class.getResourceAsStream(resource);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            String text = sb.toString();
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
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("시연용 체크리스트(" + resource + ")를 읽을 수 없습니다.", e);
        }
    }

    private static MultipartFile file(String name, String text) {
        return new InMemoryFile(name, text.getBytes(StandardCharsets.UTF_8));
    }

    /** 증적 첨부 시연용 작은 텍스트 파일 */
    private record InMemoryFile(String name, byte[] bytes) implements MultipartFile {
        @Override public String getName() { return "files"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "text/plain"; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), bytes); }
    }
}
