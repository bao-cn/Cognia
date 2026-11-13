package bao.gabriel.cognia.system.dto.oauth;

import jakarta.validation.constraints.NotBlank;

public record OAuthLinkRequest(@NotBlank(message = "关联请求无效") String linkToken) {
}
