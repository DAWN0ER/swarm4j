package priv.dawn.swarm.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.assistants.run.ToolChoice;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.function.FunctionDefinition;
import com.theokanning.openai.service.OpenAiService;
import io.reactivex.Flowable;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import priv.dawn.swarm.api.BaseAgentClient;
import priv.dawn.swarm.common.*;
import priv.dawn.swarm.enums.Roles;
import priv.dawn.swarm.enums.ToolChoices;
import priv.dawn.swarm.exceptions.ModelCallException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/19/19:53
 */

public class OpenAIClient extends BaseAgentClient {

    private final OpenAiService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAIClient(String apiKey, String baseUrl) {
        super(apiKey, baseUrl);
        service = new OpenAiService(apiKey, baseUrl);
    }

    @Override
    protected ModelResponse modelCall(Agent agent, List<AgentMessage> messages) {

        log.debug("OpenAIClient#modelCall: Agent name:{}, messages:{}", agent.getName(), gson.toJson(messages));
        try {

            ChatCompletionRequest chatCompletionRequest = buildParam(agent, messages);
            ChatCompletionResult chatCompletion = service.createChatCompletion(chatCompletionRequest);
            ChatCompletionChoice originalRsp = chatCompletion.getChoices().get(0);
            String finishReason = originalRsp.getFinishReason();
            AssistantMessage originalRspMsg = originalRsp.getMessage();
            ModelResponse response = new ModelResponse();
            switch (finishReason) {
                case "stop":
                    response.setType(ModelResponse.Type.FINISH.code);
                    response.setContent(originalRspMsg.getContent());
                    break;
                case "length":
                    response.setType(ModelResponse.Type.OVER_LENGTH.code);
                    response.setContent(originalRspMsg.getContent());
                    break;
                case "tool_calls":
                    response.setType(ModelResponse.Type.TOOL_CALL.code);
                    List<ToolCallRequest> calls = new ArrayList<>();
                    for (ChatToolCall call : originalRspMsg.getToolCalls()) {
                        ToolCallRequest toolCallRequest = cast2ToolCall(call);
                        calls.add(toolCallRequest);
                    }
                    response.setCalls(calls);
            }
            log.debug("OpenAIClient#modelCall: response:{}", response);
            return response;
        } catch (JsonProcessingException e) {
            throw new ModelCallException("Json 解析异常", e);
        } catch (Exception e) {
            throw new ModelCallException("未知异常", e);
        }
    }

    @Override
    protected Flowable<ModelResponse> modelStreamCall(Agent agent, List<AgentMessage> messages) {

        log.debug("OpenAIClient#modelStreamCall: Agent name:{}, messages:{}", agent.getName(), gson.toJson(messages));
        try {

            ChatCompletionRequest chatCompletionRequest = buildParam(agent, messages);
            Flowable<ChatCompletionChunk> orgFlowable = service.streamChatCompletion(chatCompletionRequest);
            return orgFlowable.map(this::chatChunk2Rsp);
        } catch (JsonProcessingException e) {
            throw new ModelCallException("Json解析异常", e);
        } catch (Exception e) {
            throw new ModelCallException("未知异常", e);
        }
    }

    private ChatCompletionRequest buildParam(Agent agent, List<AgentMessage> messages) throws JsonProcessingException {
        // 前期准备
        List<ChatMessage> chatMessages = new ArrayList<>();

        // 将 AgentMessage 转换为 ChatMessage
        for (AgentMessage msg : messages) {
            if (Roles.SYSTEM.value.equals(msg.getRole())) {
                chatMessages.add(new SystemMessage(msg.getContent()));
            } else if (Roles.USER.value.equals(msg.getRole())) {

                chatMessages.add(new UserMessage(msg.getContent()));
            } else if (Roles.ASSISTANT.value.equals(msg.getRole())) {
                AssistantMessage message = new AssistantMessage();
                message.setContent(msg.getContent());
                List<ToolCallRequest> toolCalls = ListUtils.emptyIfNull(msg.getToolCalls());
                List<ChatToolCall> callList = new ArrayList<>();
                for (ToolCallRequest toolCall : toolCalls) {
                    ChatToolCall call = cast2ChatCall(toolCall);
                    callList.add(call);
                }
                message.setToolCalls(callList);
                chatMessages.add(message);
            } else if (Roles.TOOL.value.equals(msg.getRole())) {
                ToolMessage toolMessage = new ToolMessage();
                toolMessage.setToolCallId(msg.getToolResult().getCallId());
                toolMessage.setContent(msg.getToolResult().getResult());
                chatMessages.add(toolMessage);
            }
        }

        // 构造参数，调用AI接口
        return ChatCompletionRequest.builder()
                .model(agent.getModel())
                .messages(chatMessages)
                .n(1)
                .maxTokens(8192)
                .tools(cast2ToolDefinition(agent.getFunctions()))
                .toolChoice(cast2ToolChoice(agent.getToolChoice()))
                .parallelToolCalls(true)
                .build();
    }

    private ChatToolCall cast2ChatCall(ToolCallRequest call) throws JsonProcessingException {
        ChatToolCall chatToolCall = new ChatToolCall();
        chatToolCall.setId(call.getCallId());
        chatToolCall.setType("function");
        // 这边默认没有设置 index，个人觉得没有必要，如果其他接口有强制要求就自己改装一下吧。
        JsonNode paramNode = mapper.createObjectNode(); // 生成一个 {}
        if (StringUtils.isNoneBlank(call.getJsonParam())) {
            paramNode = mapper.readTree(call.getJsonParam());
        }
        chatToolCall.setFunction(
                new ChatFunctionCall(call.getName(), paramNode)
        );
        return chatToolCall;
    }

    private ToolCallRequest cast2ToolCall(ChatToolCall call) throws JsonProcessingException {

        ToolCallRequest toolCall = new ToolCallRequest();
        toolCall.setCallId(call.getId());
        toolCall.setName(call.getFunction().getName());
        toolCall.setJsonParam(mapper.writeValueAsString(call.getFunction().getArguments()));
        return toolCall;
    }

    private List<ChatTool> cast2ToolDefinition(ToolRepository toolRepository) {
        if (Objects.isNull(toolRepository) || toolRepository.isEmpty()) {
            return null;
        }
        List<String> nameList = toolRepository.getNameList();
        List<ChatTool> chatTools = new ArrayList<>();
        for (String name : nameList) {
            ToolFunction tool = toolRepository.getTool(name);
            chatTools.add(new ChatTool(FunctionDefinition.builder()
                    .name(name)
                    .description(tool.getDescription())
                    .parametersDefinition(tool.getParameters())
                    .build())
            );
        }
        return chatTools;
    }

    private ToolChoice cast2ToolChoice(String choice) {
        if (ToolChoices.AUTO.value.equals(choice)) {
            return ToolChoice.AUTO;
        } else if (ToolChoices.REQUIRED.value.equals(choice)) {
            return ToolChoice.REQUIRED;
        } else if (ToolChoices.NONE.value.equals(choice)) {
            return ToolChoice.NONE;
        }
        return null;
    }

    private ModelResponse chatChunk2Rsp(ChatCompletionChunk chunk) {
        // 将 chunk 封装成 Response
        ModelResponse response = new ModelResponse();
        if (Objects.isNull(chunk) || chunk.getCreated() == 0L) {
            response.setType(ModelResponse.Type.DUPLICATED.code);
            return response;
        }
        ChatCompletionChoice choice = Optional.ofNullable(chunk.getChoices()).map(a -> a.get(0)).orElse(null);
        if (Objects.isNull(choice)) {
            response.setType(ModelResponse.Type.DUPLICATED.code);
            return response;
        }
        String finishReason = Optional.ofNullable(choice.getFinishReason()).orElse(""); // 避免 null
        switch (finishReason) {
            case "stop":
                response.setType(ModelResponse.Type.FINISH.code);
                break;
            case "tool_calls":
                response.setType(ModelResponse.Type.TOOL_CALL.code);
                break;
            case "length":
                response.setType(ModelResponse.Type.OVER_LENGTH.code);
                break;
        }
        AssistantMessage message = choice.getMessage();
        Optional.ofNullable(message).map(AssistantMessage::getContent).ifPresent(response::setContent);
        List<ChatToolCall> calls = Optional.ofNullable(message).map(AssistantMessage::getToolCalls).orElse(null);
        // calls stream 依次返回调用的部分信息，其中有 index 参数指明位置
        if (Objects.nonNull(calls) && !calls.isEmpty()) {
            List<ToolCallRequest> myCalls = new ArrayList<>();
            response.setCalls(myCalls);
            for (ChatToolCall call : calls) {
                // call 存在则默认有 index，且默认顺序递增
                int index = call.getIndex();
                while (myCalls.size() <= index) {
                    // 填充空参数
                    myCalls.add(new ToolCallRequest());
                }
                Optional.ofNullable(call.getId()).ifPresent(id -> myCalls.get(index).setCallId(id));
                Optional.ofNullable(call.getFunction()).map(ChatFunctionCall::getName)
                        .ifPresent(name -> myCalls.get(index).setName(name));
                Optional.ofNullable(call.getFunction()).map(ChatFunctionCall::getArguments).map(JsonNode::asText)
                        .ifPresent(paramStr -> myCalls.get(index).setJsonParam(paramStr));
            }
        }
        return response;

    }
}
