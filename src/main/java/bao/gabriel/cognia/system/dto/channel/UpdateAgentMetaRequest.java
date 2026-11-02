package bao.gabriel.cognia.system.dto.channel;

import jakarta.validation.constraints.Size;

public record UpdateAgentMetaRequest(
        @Size(max = 128, message = "Agent 名称不能超过 128 个字符") String name,
        @Size(max = 512, message = "Agent 描述不能超过 512 个字符") String description,
        @Size(max = 1000, message = "Agent 标签不能超过 1000 个字符") String tags
) {
}
