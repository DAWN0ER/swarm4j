package priv.dawn.swarm.common;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/19/17:08
 */

public class AgentMessage {

    private String role;

    /**
     * 注意：
     * 只有 role 为 assistant，system，user 时才会从 content 读写内容
     * 当 role 为 tool 时，tool 调用的结构存在 toolResult 中而非 content 中
     */
    private String content;

    /**
     * 下面两个分别是 tool call 的调用和响应
     * openai 可以在一个 message 中平行调用多个 tool_call，但是 DashScope 一次智能调用一个 tool_call
     * 每一个 tool 调用返回的 result 都需要一个 message 单独包装发送
     */
    private List<ToolCallRequest> toolCalls;
    private ToolCallResult toolResult;

    public AgentMessage() {
    }

    public AgentMessage(String role, String content, List<ToolCallRequest> toolCalls, ToolCallResult toolResult) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolResult = toolResult;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCallRequest> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallRequest> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public ToolCallResult getToolResult() {
        return toolResult;
    }

    public void setToolResult(ToolCallResult toolResult) {
        this.toolResult = toolResult;
    }
}
