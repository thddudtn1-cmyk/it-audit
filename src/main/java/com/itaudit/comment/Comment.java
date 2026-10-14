package com.itaudit.comment;

import java.time.LocalDateTime;

import com.itaudit.auth.Role;

/** 코멘트 (comment_note). 조회 시 작성자 이름·역할을 조인해 채운다. */
public class Comment {

    private Long commentId;
    private CommentTarget targetType;
    private Long targetId;
    private String body;
    private Long authorId;
    private String authorName;
    private Role authorRole;
    private LocalDateTime createdAt;
    private String deletedYn;

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public CommentTarget getTargetType() { return targetType; }
    public void setTargetType(CommentTarget targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public Role getAuthorRole() { return authorRole; }
    public void setAuthorRole(Role authorRole) { this.authorRole = authorRole; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getDeletedYn() { return deletedYn; }
    public void setDeletedYn(String deletedYn) { this.deletedYn = deletedYn; }
}
