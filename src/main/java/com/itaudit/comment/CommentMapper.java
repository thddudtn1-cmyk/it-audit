package com.itaudit.comment;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentMapper {

    /** 삭제되지 않은 코멘트 (오래된 순) */
    List<Comment> findActive(@Param("targetType") CommentTarget targetType, @Param("targetId") Long targetId);

    Comment findById(@Param("commentId") Long commentId);

    void insert(Comment comment);

    int markDeleted(@Param("commentId") Long commentId, @Param("deletedBy") Long deletedBy,
                    @Param("deletedAt") LocalDateTime deletedAt);
}
