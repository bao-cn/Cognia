package bao.gabriel.cognia.system.mapper;

import bao.gabriel.cognia.system.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * Bypasses the global logic-delete filter so the cleanup task can see
     * soft-deleted users whose files have not been purged yet.
     */
    @Select("""
            SELECT *
            FROM sys_user
            WHERE is_deleted = TRUE
              AND files_cleaned_at IS NULL
            ORDER BY id
            LIMIT #{limit}
            """)
    List<User> selectDeletedPendingFileCleanup(@Param("limit") int limit);

    /**
     * Counts both active users and soft-deleted users whose files have not
     * been cleaned yet, so a username cannot be reused before cleanup.
     */
    @Select("""
            SELECT COUNT(*)
            FROM sys_user
            WHERE (is_deleted = FALSE OR files_cleaned_at IS NULL)
              AND LOWER(username) = LOWER(#{username})
            """)
    long countByUsernameIncludingDeleted(@Param("username") String username);

    /**
     * Releases the username only after the owner's files have been purged, so
     * the namespace stays reserved during the whole retention window.
     */
    @Update("""
            UPDATE sys_user
            SET files_cleaned_at = #{time},
                avatar = NULL,
                username = CONCAT('deleted-', id)
            WHERE id = #{id}
            """)
    int markFilesCleaned(@Param("id") Long id, @Param("time") LocalDateTime time);
}
