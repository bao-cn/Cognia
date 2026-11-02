package bao.gabriel.cognia.system.mapper;

import bao.gabriel.cognia.system.entity.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("""
            SELECT r.*
            FROM sys_role r
                     JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.is_deleted = FALSE
              AND r.status = 1
            ORDER BY r.sort ASC, r.id ASC
            """)
    List<Role> selectEnabledByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM sys_user_role ur
                     JOIN sys_user u ON u.id = ur.user_id AND u.is_deleted = FALSE
            WHERE ur.role_id = #{roleId}
            """)
    long countUsersByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT COUNT(*)
            FROM sys_user_role ur
                     JOIN sys_user u ON u.id = ur.user_id AND u.is_deleted = FALSE
            WHERE ur.role_id = #{roleId}
              AND u.status = 1
            """)
    long countEnabledUsersByRoleId(@Param("roleId") Long roleId);

    @Select("""
            SELECT r.*
            FROM sys_role r
            WHERE r.code = #{code}
              AND r.is_deleted = FALSE
            LIMIT 1
            """)
    Role selectByCode(@Param("code") String code);
}
