package com.itaudit.evidence;

import java.time.LocalDateTime;

/** 증적·증빙자료 파일 메타데이터 (evidence). 파일 본문은 data/files 아래에 둔다. */
public class Evidence {

    private Long evidenceId;
    private EvidenceOwnerType ownerType;
    private Long ownerId;
    private String originalName;
    /** data 디렉터리 기준 상대 경로 */
    private String storedPath;
    private long fileSize;
    private String contentType;
    private Long uploadedBy;
    private String uploaderName;
    private LocalDateTime uploadedAt;
    private String deletedYn;
    private Long deletedBy;
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return "Y".equals(deletedYn);
    }

    /** 화면 표시용 크기 (KB/MB) */
    public String getSizeLabel() {
        if (fileSize < 1024 * 1024) {
            return Math.max(1, Math.round(fileSize / 1024.0)) + " KB";
        }
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }

    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
    public EvidenceOwnerType getOwnerType() { return ownerType; }
    public void setOwnerType(EvidenceOwnerType ownerType) { this.ownerType = ownerType; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public String getUploaderName() { return uploaderName; }
    public void setUploaderName(String uploaderName) { this.uploaderName = uploaderName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getDeletedYn() { return deletedYn; }
    public void setDeletedYn(String deletedYn) { this.deletedYn = deletedYn; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
