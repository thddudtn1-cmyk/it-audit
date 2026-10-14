package com.itaudit.checklist;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.itaudit.auth.LoginUser;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;

/**
 * 체크리스트 마스터 (F-02).
 * 연도별 개정본: 적용연도마다 항목 집합을 따로 두고, 새 연도는 직전 연도 복사 또는 엑셀 업로드로 만든다.
 * 항목은 삭제하지 않고 사용여부(N)로 뺀다.
 */
@Service
public class ChecklistService {

    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;

    private final ChecklistMapper mapper;
    private final ActivityLogService activityLog;

    public ChecklistService(ChecklistMapper mapper, ActivityLogService activityLog) {
        this.mapper = mapper;
        this.activityLog = activityLog;
    }

    public List<Integer> findYears() {
        return mapper.findYears();
    }

    public int countByYear(int year) {
        return mapper.countByYear(year);
    }

    public boolean isUsedInRounds(int year) {
        return mapper.countUsedInRounds(year) > 0;
    }

    public List<ChecklistItem> findItems(int year, AuditArea area, AuditPeriod period) {
        return mapper.findByYear(year, area, period);
    }

    /** 같은 감사 영역·감사 항목이 연속되면 셀을 합칠 수 있도록 rowspan 계산 */
    public List<ChecklistRow> toRows(List<ChecklistItem> items) {
        List<ChecklistRow> rows = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            ChecklistItem cur = items.get(i);
            ChecklistItem prev = i > 0 ? items.get(i - 1) : null;
            int areaSpan = 0;
            if (prev == null || prev.getAuditArea() != cur.getAuditArea()) {
                areaSpan = 1;
                while (i + areaSpan < items.size() && items.get(i + areaSpan).getAuditArea() == cur.getAuditArea()) {
                    areaSpan++;
                }
            }
            int itemSpan = 0;
            if (prev == null || !sameAuditItem(prev, cur)) {
                itemSpan = 1;
                while (i + itemSpan < items.size() && sameAuditItem(items.get(i + itemSpan), cur)) {
                    itemSpan++;
                }
            }
            rows.add(new ChecklistRow(cur, areaSpan, itemSpan));
        }
        return rows;
    }

    private static boolean sameAuditItem(ChecklistItem a, ChecklistItem b) {
        return a.getAuditArea() == b.getAuditArea() && Objects.equals(a.getAuditItem(), b.getAuditItem());
    }

    public ChecklistItem getItem(Long itemId) {
        ChecklistItem item = mapper.findById(itemId);
        if (item == null) {
            throw new BusinessException("체크리스트 항목을 찾을 수 없습니다.");
        }
        return item;
    }

    public List<String> findAuditItems(int year) {
        return mapper.findAuditItems(year);
    }

    public List<String> findRegulations(int year) {
        return mapper.findRegulations(year);
    }

    // ---------- 엑셀 업로드 ----------

    /**
     * 업로드 미리보기를 저장한다. 같은 연도에 항목이 있으면 모두 대체한다.
     * 해당 연도 항목으로 분기 회차를 이미 만들었으면 대체할 수 없다 (회차 이력 보존).
     */
    @Transactional
    public int saveUpload(ChecklistUploadPreview preview, LoginUser actor) {
        checkYear(preview.getApplyYear());
        if (!preview.isValid()) {
            throw new BusinessException("오류가 있는 행이 있어 저장할 수 없습니다.");
        }
        int year = preview.getApplyYear();
        if (isUsedInRounds(year)) {
            throw new BusinessException(year + "년 체크리스트로 만든 분기 회차가 있어 엑셀로 대체할 수 없습니다. 항목을 개별 수정하세요.");
        }
        int replaced = mapper.deleteByYear(year);
        LocalDateTime now = LocalDateTime.now();
        for (ChecklistUploadRow row : preview.getRows()) {
            ChecklistItem item = new ChecklistItem();
            item.setApplyYear(year);
            item.setSeqNo(row.getSeqNo());
            item.setAuditArea(row.getAuditArea());
            item.setAuditItem(row.getAuditItem());
            item.setItemDetail(row.getItemDetail());
            item.setAuditPeriod(row.getAuditPeriod());
            item.setRegulation(emptyToNull(row.getRegulation()));
            item.setUseYn("Y");
            stamp(item, actor, now, true);
            mapper.insert(item);
        }
        activityLog.log(ActivityLogService.CHECKLIST_UPLOAD, "CHECKLIST", year,
                "파일=" + preview.getFileName() + ", 등록 " + preview.getRows().size() + "건"
                        + (replaced > 0 ? ", 기존 " + replaced + "건 대체" : ""));
        return preview.getRows().size();
    }

    // ---------- 개정본 복사 ----------

    @Transactional
    public int copyYear(int fromYear, int toYear, LoginUser actor) {
        checkYear(toYear);
        if (fromYear == toYear) {
            throw new BusinessException("원본 연도와 대상 연도가 같습니다.");
        }
        if (mapper.countByYear(toYear) > 0) {
            throw new BusinessException(toYear + "년 체크리스트가 이미 있습니다. 항목을 개별 수정하거나 엑셀로 다시 올리세요.");
        }
        List<ChecklistItem> source = mapper.findByYear(fromYear, null, null);
        if (source.isEmpty()) {
            throw new BusinessException(fromYear + "년 체크리스트가 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        for (ChecklistItem item : source) {
            item.setItemId(null);
            item.setApplyYear(toYear);
            stamp(item, actor, now, true);
            mapper.insert(item);
        }
        activityLog.log(ActivityLogService.CHECKLIST_COPY, "CHECKLIST", toYear,
                fromYear + "년 → " + toYear + "년, " + source.size() + "건");
        return source.size();
    }

    // ---------- 개별 등록·수정 ----------

    @Transactional
    public Long create(int year, ChecklistForm form, LoginUser actor) {
        checkYear(year);
        if (mapper.findBySeqNo(year, form.getSeqNo()) != null) {
            throw new BusinessException(year + "년에 순번 " + form.getSeqNo() + "이(가) 이미 있습니다.");
        }
        ChecklistItem item = new ChecklistItem();
        item.setApplyYear(year);
        apply(item, form);
        stamp(item, actor, LocalDateTime.now(), true);
        mapper.insert(item);
        activityLog.log(ActivityLogService.CHECKLIST_CREATE, "CHECKLIST_ITEM", item.getItemId(),
                year + "년 #" + item.getSeqNo());
        return item.getItemId();
    }

    @Transactional
    public void update(Long itemId, ChecklistForm form, LoginUser actor) {
        ChecklistItem item = getItem(itemId);
        ChecklistItem dup = mapper.findBySeqNo(item.getApplyYear(), form.getSeqNo());
        if (dup != null && !dup.getItemId().equals(itemId)) {
            throw new BusinessException(item.getApplyYear() + "년에 순번 " + form.getSeqNo() + "이(가) 이미 있습니다.");
        }
        String before = describe(item);
        apply(item, form);
        stamp(item, actor, LocalDateTime.now(), false);
        mapper.update(item);
        activityLog.log(ActivityLogService.CHECKLIST_UPDATE, "CHECKLIST_ITEM", itemId,
                "변경 전 " + before + " → 변경 후 " + describe(item));
    }

    private static void apply(ChecklistItem item, ChecklistForm form) {
        item.setSeqNo(form.getSeqNo());
        item.setAuditArea(form.getAuditArea());
        item.setAuditItem(form.getAuditItem().trim());
        item.setItemDetail(form.getItemDetail().trim());
        item.setAuditPeriod(form.getAuditPeriod());
        item.setRegulation(emptyToNull(form.getRegulation()));
        item.setUseYn(form.isActive() ? "Y" : "N");
    }

    private static void stamp(ChecklistItem item, LoginUser actor, LocalDateTime now, boolean created) {
        Long userId = actor == null ? null : actor.getUserId();
        if (created) {
            item.setCreatedBy(userId);
            item.setCreatedAt(now);
        }
        item.setUpdatedBy(userId);
        item.setUpdatedAt(now);
    }

    private static String describe(ChecklistItem i) {
        String detail = i.getItemDetail();
        if (detail.length() > 60) {
            detail = detail.substring(0, 60) + "…";
        }
        return "[#" + i.getSeqNo() + " " + i.getAuditArea().getLabel() + " / " + i.getAuditItem() + " / " + detail
                + " / " + i.getAuditPeriod().getLabel() + " / " + i.getRegulation() + " / 사용=" + i.getUseYn() + "]";
    }

    static void checkYear(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessException("적용연도는 " + MIN_YEAR + "~" + MAX_YEAR + " 사이여야 합니다.");
        }
    }

    private static String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
