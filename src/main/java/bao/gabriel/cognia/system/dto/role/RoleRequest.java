package bao.gabriel.cognia.system.dto.role;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoleRequest(
        @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$",
                message = "角色编码需以大写字母开头，只能包含大写字母、数字、下划线，长度 2-32")
        String code,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过 64 个字符")
        String name,

        @Size(max = 255, message = "角色描述长度不能超过 255 个字符")
        String description,

        @Min(value = 0, message = "排序值不能为负数")
        Integer sort,

        @Min(value = 0, message = "状态只能为 0 或 1")
        @Max(value = 1, message = "状态只能为 0 或 1")
        Integer status,

        List<Long> permissionIds
) {
}
