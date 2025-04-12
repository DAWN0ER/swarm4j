package priv.dawn.swarm.domain;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.tools.ToolFunction;
import com.alibaba.dashscope.tools.*;
import com.google.gson.JsonObject;
import io.reactivex.Flowable;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import priv.dawn.swarm.api.BaseAgentClient;
import priv.dawn.swarm.common.*;
import priv.dawn.swarm.enums.Roles;
import priv.dawn.swarm.exceptions.ModelCallException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/21/20:47
 */

public class DashScopeClient extends BaseAgentClient {

    private final Generation service = new Generation();

    public DashScopeClient(String apiKey, String baseUrl) {
        super(apiKey, baseUrl);
    }

    @Override
    protected ModelResponse modelCall(Agent agent, List<AgentMessage> messages) {

        log.debug("DashScopeClient#modelCall: Agent name:{}, messages:{}", agent.getName(), gson.toJson(messages));

        GenerationParam param = buildParam(agent, messages, false);
        GenerationResult result;
        try {
            result = service.call(param);
        } catch (NoApiKeyException e) {
            throw new ModelCallException("ApiKey不存在或异常", e);
        } catch (InputRequiredException e) {
            throw new ModelCallException("请求参数异常", e);
        }
        GenerationOutput.Choice choice = result.getOutput().getChoices().get(0);
        Message message = choice.getMessage();
        String finishReason = choice.getFinishReason();
        ModelResponse response = new ModelResponse();
        switch (finishReason) {
            case "stop":
                response.setType(ModelResponse.Type.FINISH.code);
                response.setContent(message.getContent());
                break;
            case "length":
                response.setType(ModelResponse.Type.OVER_LENGTH.code);
                response.setContent(message.getContent());
                break;
            case "tool_calls":
                response.setType(ModelResponse.Type.TOOL_CALL.code);
                // 现在只支持返回一个 function, 目前这边只支持 ToolCallFunction
                List<ToolCallBase> calls = message.getToolCalls();
                List<ToolCallRequest> toolCalls = calls.stream().map(call -> {
                    ToolCallFunction toolCallFunc = (ToolCallFunction) call;
                    ToolCallFunction.CallFunction function = toolCallFunc.getFunction();
                    ToolCallRequest mapCall = new ToolCallRequest();
                    mapCall.setCallId(toolCallFunc.getId());
                    mapCall.setJsonParam(function.getArguments());
                    mapCall.setName(function.getName());
                    return mapCall;
                }).collect(Collectors.toList());
                response.setCalls(toolCalls);
        }

        log.debug("DashScopeClient#modelCall: response:{}", response);

        return response;
    }

    @Override
    protected Flowable<ModelResponse> modelStreamCall(Agent agent, List<AgentMessage> messages) {

        log.debug("DashScopeClient#modelStreamCall agent name:{}, messages:{}", agent.getName(), gson.toJson(messages));

        GenerationParam param = buildParam(agent, messages, true);
        Flowable<GenerationResult> streamCall;
        try {
            streamCall = service.streamCall(param);
        } catch (NoApiKeyException e) {
            throw new ModelCallException("ApiKey不存在或异常", e);
        } catch (InputRequiredException e) {
            throw new ModelCallException("请求参数异常", e);
        }
        return streamCall.map(this::streamCall2Rsp);
    }

    private GenerationParam buildParam(Agent agent, List<AgentMessage> messages, boolean stream) {
        List<Message> sendMsgList = messages.stream().map(this::buildMsg).collect(Collectors.toList());
        return GenerationParam.builder()
                .messages(sendMsgList)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .apiKey(this.apiKey)
                .model(agent.getModel())
                .tools(buildTools(agent))
                .toolChoice(agent.getToolChoice())
                .incrementalOutput(stream)
                .build();
    }

    private Message buildMsg(AgentMessage agentMsg) {
        // user，system，没有 tool_calls 的 assistant 可以直接填入
        Message baseMsg = Message.builder()
                .role(agentMsg.getRole())
                .content(agentMsg.getContent())
                .build();
        // 处理 tool_calls
        if (Roles.ASSISTANT.value.equals(agentMsg.getRole())) {
            List<ToolCallRequest> calls = ListUtils.emptyIfNull(agentMsg.getToolCalls());
            List<ToolCallBase> toolCalls = calls.stream().map(originalCall -> {
                JsonObject func = new JsonObject();
                func.addProperty("name", originalCall.getName());
                func.addProperty("arguments", originalCall.getJsonParam());
                ToolCallFunction toolCall = new ToolCallFunction();
                toolCall.setId(originalCall.getCallId());
                toolCall.setFunction(gson.fromJson(func, ToolCallFunction.CallFunction.class));
                return (ToolCallBase) toolCall;
            }).collect(Collectors.toList());
            baseMsg.setToolCalls(toolCalls);
        }
        // 处理 role 为 tool 时的信息
        if (Roles.TOOL.value.equals(agentMsg.getRole())) {
            ToolCallResult toolResult = agentMsg.getToolResult();
            baseMsg.setName(toolResult.getName());
            baseMsg.setToolCallId(toolResult.getCallId());
            baseMsg.setContent(toolResult.getResult());
        }
        return baseMsg;
    }

    private List<ToolBase> buildTools(Agent agent) {
        if (Objects.isNull(agent.getFunctions()) || agent.getFunctions().isEmpty()) {
            return null;
        }
        List<String> nameList = agent.getFunctions().getNameList();
        return nameList.stream().map(name -> {
            priv.dawn.swarm.common.ToolFunction tool = agent.getFunctions().getTool(name);
            String parameters = tool.getParameterSchema();
            String description = tool.getDescription();
            JsonObject param = null;
            if (StringUtils.isNotBlank(parameters)) {
                param = gson.fromJson(parameters, JsonObject.class);
            }
            return (ToolBase) ToolFunction.builder()
                    .function(
                            FunctionDefinition.builder()
                                    .name(name)
                                    .description(description)
                                    .parameters(param)
                                    .build()
                    ).build();
        }).collect(Collectors.toList());
    }

    private ModelResponse streamCall2Rsp(GenerationResult streamCall) {

        ModelResponse response = new ModelResponse();
        GenerationOutput.Choice choice = streamCall.getOutput().getChoices().get(0);
        if (Objects.isNull(choice)) {
            response.setType(ModelResponse.Type.DUPLICATED.code);
            return response;
        }
        Message message = choice.getMessage();
        if (Objects.nonNull(message.getContent())) {
            response.setContent(message.getContent());
        }
        // 处理 tool_calls
        if (Objects.nonNull(message.getToolCalls()) && !message.getToolCalls().isEmpty()) {
            List<ToolCallBase> calls = message.getToolCalls();
            ToolCallRequest myCall = new ToolCallRequest();
            // 目前来说，每次也就调用一个 function
            calls.forEach(call -> {
                ToolCallFunction toolCallFunc = (ToolCallFunction) call;
                myCall.setCallId(toolCallFunc.getId());
                if (Objects.nonNull(toolCallFunc.getFunction())) {
                    ToolCallFunction.CallFunction function = toolCallFunc.getFunction();
                    myCall.setName(function.getName());
                    myCall.setJsonParam(function.getArguments());
                }
            });
            response.setCalls(Collections.singletonList(myCall));
        }
        // finish reason
        String finishReason = Optional.ofNullable(choice.getFinishReason()).orElse("");
        switch (finishReason) {
            case "stop":
                response.setType(ModelResponse.Type.FINISH.code);
                break;
            case "length":
                response.setType(ModelResponse.Type.OVER_LENGTH.code);
                break;
            case "tool_calls":
                response.setType(ModelResponse.Type.TOOL_CALL.code);
                break;
        }
        return response;
    }
}
