package priv.dawn.swarm.api;

import com.google.gson.Gson;
import io.reactivex.Flowable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import priv.dawn.swarm.common.*;
import priv.dawn.swarm.enums.Roles;
import priv.dawn.swarm.exceptions.ModelCallException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 * Description: AgentClient 模板
 *
 * @author Dawn Yang
 * @since 2025/01/19/17:15
 */

public abstract class BaseAgentClient implements AgentClient {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final Gson gson = new Gson();

    protected String apiKey;
    protected String baseUrl;
    protected ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();

    protected abstract ModelResponse modelCall(Agent agent, List<AgentMessage> messages) throws ModelCallException;

    protected abstract Flowable<ModelResponse> modelStreamCall(Agent agent, List<AgentMessage> messages) throws ModelCallException;

    public BaseAgentClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public List<AgentMessage> run(Agent agent, List<AgentMessage> messages, int maxTurn) {
        verifyArguments(agent,messages,maxTurn);
        log.debug("Client run: agent:{}, messages:{}, maxTurn:{}", agent.getName(), gson.toJson(messages), maxTurn);

        List<AgentMessage> appendMsg = new ArrayList<>(maxTurn);
        AgentMessage prompt = new AgentMessage();
        prompt.setRole(Roles.SYSTEM.value);
        prompt.setContent(agent.getInstructions());

        while (appendMsg.size() < maxTurn) {
            List<AgentMessage> allMsg = new ArrayList<>(messages.size() + appendMsg.size() + 1);
            allMsg.add(prompt);
            allMsg.addAll(messages);
            allMsg.addAll(appendMsg);

            // 获取模型响应
            ModelResponse response = modelCall(agent, allMsg);
            AgentMessage message = castFromModelRsp(response);
            appendMsg.add(message);

            // 如果响应是对话结束
            if (ModelResponse.Type.FINISH.code == response.getType()) {
                log.debug("Client run end. total turns:{}", appendMsg.size());
                return appendMsg;
            }

            // 处理函数调用
            if (ModelResponse.Type.TOOL_CALL.code == response.getType()) {
                List<ToolCallRequest> calls = response.getCalls();
                List<AgentMessage> toolResults = handleFunctionCall(calls, agent);
                appendMsg.addAll(toolResults);
            }
        }
        // 证明已经达到 maxTurn
        log.warn("Client run reach max turn! maxTurn={}", maxTurn);
        return appendMsg;
    }

    @Override
    public Flowable<AgentStreamMessage> streamRun(Agent agent, List<AgentMessage> messages, int maxTurn) {
        verifyArguments(agent,messages,maxTurn);
        log.debug("Client stream run: agent:{}, messages:{}, maxTurn:{}",
                agent.getName(), gson.toJson(messages), maxTurn);
        FlowableProcessor<AgentStreamMessage> processor = PublishProcessor.create();
        singleThreadPool.execute(() -> asyncRun(processor, agent, messages, maxTurn));
        return Flowable.fromPublisher(processor).onBackpressureBuffer();
    }

    /**
     * streamRun 的实际执行函数，异步多线程调用
     */
    private void asyncRun(
            FlowableProcessor<AgentStreamMessage> processor,
            Agent agent,
            List<AgentMessage> messages,
            int maxTurn
    ) {

        AgentMessage prompt = new AgentMessage();
        prompt.setRole(Roles.SYSTEM.value);
        prompt.setContent(agent.getInstructions());
        List<AgentMessage> appendMsg = new ArrayList<>(maxTurn);

        while (appendMsg.size() < maxTurn) {
            List<AgentMessage> allMsg = new ArrayList<>(messages.size() + appendMsg.size() + 1);
            allMsg.add(prompt);
            allMsg.addAll(messages);
            allMsg.addAll(appendMsg);

            // 后续使用的 rsp，所有字段覆盖只允许的深拷贝，并且非 null 字段不允许被 null 覆盖
            ModelResponse thisTurnRsp = new ModelResponse();
            int thisTurn = appendMsg.size(); // idx in appendMsg
            // 获取模型响应
            Flowable<ModelResponse> responseFlowable = modelStreamCall(agent, allMsg);
            // 等待响应的 stream 完成，Response 也全部填充完成，继续走流程就行
            responseFlowable.blockingForEach(chunk -> {
                if (chunk.getType() == ModelResponse.Type.DUPLICATED.code) {
                    return;
                }
                // 覆盖 ModelResponse 中需要的字段
                thisTurnRsp.coverFiledWith(chunk);
                // 封装 flowable 信息
                AgentStreamMessage streamMsg = new AgentStreamMessage();
                streamMsg.setMsgIndex(thisTurn);
                // 封装残缺的 msg, 里面的所有引用都来自 model rsp
                AgentMessage message = castFromModelRsp(chunk);
                streamMsg.setMessage(message);
                // 发送 flowable
                processor.onNext(streamMsg);
            });

            // 此时 stream 的所有字段都存储完成。
            log.debug("Stream turn:{}, completed response:{}",thisTurn, gson.toJson(thisTurnRsp));
            AgentMessage message = castFromModelRsp(thisTurnRsp);
            appendMsg.add(message);
            // 如果响应是结束，则返回
            if (ModelResponse.Type.FINISH.code == thisTurnRsp.getType()) {
                log.debug("Client stream run end. total turns:{}", appendMsg.size());
                processor.onComplete();
                return;
            }

            // 处理函数调用
            if (ModelResponse.Type.TOOL_CALL.code == thisTurnRsp.getType()) {
                List<ToolCallRequest> calls = thisTurnRsp.getCalls();
                List<AgentMessage> toolResultsMsg = handleFunctionCall(calls, agent);
                // 需要把每个 tool Result msg 都 flowable 发送一次。
                toolResultsMsg.forEach(msg -> {
                    AgentStreamMessage streamMessage = new AgentStreamMessage();
                    streamMessage.setMsgIndex(appendMsg.size());
                    streamMessage.setMessage(msg);
                    processor.onNext(streamMessage);
                    appendMsg.add(msg);
                });
            }
        }
        // 证明已经达到 maxTurn
        log.warn("Client stream run reach max turn! maxTurn={}", maxTurn);
        processor.onComplete();
    }

    /**
     * 参数校验
     */
    private void verifyArguments(Agent agent, List<AgentMessage> messages, int maxTurn) {
        Validate.notNull(agent, "Agent can`t be null.");
        Validate.notNull(agent, "Messages can`t be null.");
        Validate.notBlank(agent.getModel(), "Agent.model can`t be null.");
        Validate.notEmpty(messages, "messages can't be empty or null.");
        Validate.isTrue(maxTurn > 0, "maxTurn must > 0.");

    }

    /**
     *
     * @param calls 工具调用请求
     * @param agent 智能体
     * @return 完成的工具调用的结果的 result，格式为 {”role“: "tool","content":"__result__"...}
     */
    private List<AgentMessage> handleFunctionCall(List<ToolCallRequest> calls, Agent agent) {
        ToolRepository toolRepository = agent.getFunctions();
        List<AgentMessage> toolMsgList = new ArrayList<>();
        for (ToolCallRequest call : calls) {
            ToolFunction tool = Optional.ofNullable(toolRepository)
                    .map(f->f.getTool(call.getName()))
                    .orElse(null);
            if (Objects.isNull(tool)) {
                // 未找到 tool 的处理逻辑
                log.warn("Tool Name:{} not Found!", call.getName());
                ToolCallResult noneResult = new ToolCallResult();
                noneResult.setResult("Error: Tool \"" + call.getName() + "\" not found.");
                noneResult.setCallId(call.getCallId());
                noneResult.setName(call.getName());
                AgentMessage message = new AgentMessage();
                message.setRole(Roles.TOOL.value);
                message.setToolResult(noneResult);
                toolMsgList.add(message);
                continue;
            }
            log.debug("Handle tool call. id:{}, tool name: {}, args: {}.",
                    call.getCallId(), call.getName(), call.getJsonParam());
            String stringResult = tool.getCallableFunction().call(call.getJsonParam());
            // 如果是 null 的话就转换为 ”“
            stringResult = Optional.ofNullable(stringResult).orElse("");
            ToolCallResult result = new ToolCallResult();
            result.setCallId(call.getCallId());
            result.setName(call.getName());
            result.setResult(stringResult);
            log.debug("Tool call result: id:{}, tool name: {}, result: {}.",
                    result.getCallId(), result.getName(), result.getResult());
            AgentMessage message = new AgentMessage();
            message.setRole(Roles.TOOL.value);
            message.setToolResult(result);
            toolMsgList.add(message);
        }
        return toolMsgList;
    }

    /**
     * 把 model 接口的 response 的需要字段全部塞进去
     *
     * @param rsp ModelCall 或者 ModelSteamCAll 的返回响应
     * @return 携带 content 和 toolCalls 的 Message
     */
    private AgentMessage castFromModelRsp(ModelResponse rsp) {
        AgentMessage msg = new AgentMessage();
        msg.setRole(Roles.ASSISTANT.value);
        msg.setContent(rsp.getContent());
        msg.setToolCalls(rsp.getCalls());
        return msg;
    }

}
