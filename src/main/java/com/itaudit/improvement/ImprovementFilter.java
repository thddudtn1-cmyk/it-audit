package com.itaudit.improvement;

/** 관리대장 조회 조건 */
public class ImprovementFilter {

    /** 종결 여부 보기 */
    public enum Closed { OPEN, CLOSED, ALL }

    private ImprovementKind kind = ImprovementKind.IMPROVEMENT;
    private Closed closed = Closed.OPEN;
    private ActionResult actionResult;
    private Long roundId;
    /** 점검 담당자는 본인 건만 */
    private Long assigneeId;
    /** 개선예정일 경과만 */
    private boolean overdueOnly;
    /** 완료로 올라와 주관자 검토(최종확정·반려)를 기다리는 건만 */
    private boolean reviewPendingOnly;

    public ImprovementKind getKind() { return kind; }
    public void setKind(ImprovementKind kind) { this.kind = kind; }
    public Closed getClosed() { return closed; }
    public void setClosed(Closed closed) { this.closed = closed; }
    public ActionResult getActionResult() { return actionResult; }
    public void setActionResult(ActionResult actionResult) { this.actionResult = actionResult; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public boolean isReviewPendingOnly() { return reviewPendingOnly; }
    public void setReviewPendingOnly(boolean reviewPendingOnly) { this.reviewPendingOnly = reviewPendingOnly; }
    public boolean isOverdueOnly() { return overdueOnly; }
    public void setOverdueOnly(boolean overdueOnly) { this.overdueOnly = overdueOnly; }
}
