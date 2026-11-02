package bao.gabriel.cognia.system.mapper;

import bao.gabriel.cognia.system.entity.Team;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TeamMapper extends BaseMapper<Team> {

    @Select("""
            SELECT *
            FROM sys_team
            WHERE is_deleted = TRUE
              AND files_cleaned_at IS NULL
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Team> selectDeletedPendingFileCleanup(@Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM sys_team
            WHERE (is_deleted = FALSE OR files_cleaned_at IS NULL)
              AND LOWER(slug) = LOWER(#{slug})
            """)
    long countBySlugIncludingDeleted(@Param("slug") String slug);

    @Update("""
            UPDATE sys_team
            SET files_cleaned_at = #{time},
                logo_url = NULL,
                slug = LEFT(slug, 32) || '-del-' || id
            WHERE id = #{id}
            """)
    int markFilesCleaned(@Param("id") Long id, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE sys_team
            SET is_deleted = TRUE,
                deleted_at = #{time},
                update_time = #{time},
                invite_code = NULL
            WHERE id = #{id}
              AND is_deleted = FALSE
            """)
    int softDelete(@Param("id") Long id, @Param("time") LocalDateTime time);
}
