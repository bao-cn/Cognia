package bao.gabriel.cognia.common.ai;

import bao.gabriel.cognia.common.ai.ChatProtocolClient.Listener;
import bao.gabriel.cognia.common.ai.ChatProtocolClient.Request;
import bao.gabriel.cognia.common.ai.ChatProtocolClient.Result;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.entity.ModelProvider;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes a streaming chat request to the {@link ChatProtocolClient} registered
 * for the model's protocol. Unknown or missing protocols fall back to the
 * OpenAI Chat Completions client, which is the project default.
 */
@Component
public class ChatProtocolDispatcher {

    private static final String DEFAULT_PROTOCOL = ModelProvider.PROTOCOL_OPENAI_CHAT;

    private final Map<String, ChatProtocolClient> clients = new LinkedHashMap<>();

    public ChatProtocolDispatcher(List<ChatProtocolClient> clients) {
        if (clients == null) {
            return;
        }
        for (ChatProtocolClient client : clients) {
            if (client != null && client.protocol() != null) {
                this.clients.put(client.protocol(), client);
            }
        }
    }

    public Result stream(ResolvedModel model, ArrayNode messages, String effort, boolean includeTools,
                         ArrayNode tools, Listener listener) {
        ChatProtocolClient client = resolve(model);
        String extraBody = model == null ? null : model.extraBody();
        return client.stream(new Request(model, messages, effort, includeTools, tools, extraBody), listener);
    }

    private ChatProtocolClient resolve(ResolvedModel model) {
        String protocol = model == null ? null : model.protocol();
        ChatProtocolClient client = protocol == null ? null : clients.get(protocol);
        if (client == null) {
            client = clients.get(DEFAULT_PROTOCOL);
        }
        if (client == null) {
            throw BusinessException.badRequest("没有可用的模型协议客户端：" + (protocol == null ? "未知" : protocol));
        }
        return client;
    }
}
