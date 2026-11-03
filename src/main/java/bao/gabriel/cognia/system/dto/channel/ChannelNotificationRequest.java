package bao.gabriel.cognia.system.dto.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChannelNotificationRequest(
        @NotBlank(message = "通知标题不能为空")
        @Size(max = 200, message = "通知标题不能超过 200 个字符") String title,
        @NotBlank(message = "通知内容不能为空")
        @Size(max = 2000, message = "通知内容不能超过 2000 个字符") String content
) {
}
