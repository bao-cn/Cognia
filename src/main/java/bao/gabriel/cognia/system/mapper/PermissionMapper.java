package bao.gabriel.cognia.system.mapper;

import bao.gabriel.cognia.system.entity.Permission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    @Select("""
            SELECT DISTINCT p.*
            FROM sys_permission p
                     JOIN sys_role_permission rp ON rp.permission_id = p.id
                     JOIN sys_role r ON r.id = rp.role_id
                     JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.is_deleted = FALSE
              AND r.status = 1
              AND p.is_deleted = FALSE
            ORDER BY p.sort ASC, p.id ASC
            """)
    List<Permission> selectByUserId(@Param("userId") Long userId);
}
