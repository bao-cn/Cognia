package bao.gabriel.cognia.system.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: only non-null fields are applied. Role assignment has its own
 * endpoint ({@code PUT /users/{id}/roles}) and its own permission, so it cannot
 * be smuggled through a profile update.
 */
public record UserUpdateRequest(
        @Size(max = 32, message = "昵称长度不能超过 32 个字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
        String email,

        @Pattern(regexp = "^$|^\\+?[0-9\\-\\s]{5,20}$", message = "手机号格式不正确")
        String phone,

        @Size(max = 512, message = "头像地址过长")
        String avatar,

        @Min(value = 0, message = "状态只能为 0 或 1")
        @Max(value = 1, message = "状态只能为 0 或 1")
        Integer status,

        @Size(max = 255, message = "备注长度不能超过 255 个字符")
        String remark
) {
}
