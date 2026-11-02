package bao.gabriel.cognia.system.dto.legal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLegalDocumentRequest(
        @NotBlank(message = "文档标题不能为空")
        @Size(max = 128, message = "文档标题长度不能超过 128 个字符")
        String title,

        @NotBlank(message = "文档内容不能为空")
        @Size(max = 200000, message = "文档内容过长")
        String content
) {
}
