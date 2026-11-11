package bao.gabriel.cognia.system.dto.user;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @jakarta.validation.constraints.Pattern(
                regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$",
                message = "用户名需以字母开头，3-32 位字母、数字或下划线")
        String username,

        @Size(max = 32, message = "昵称长度不能超过 32 个字符")
        String nickname,

        @Size(max = 512, message = "头像地址过长")
        String avatar
) {
}
