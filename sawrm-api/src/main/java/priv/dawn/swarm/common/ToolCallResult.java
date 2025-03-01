package priv.dawn.swarm.common;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/18/21:56
 */

public class ToolCallResult {

    private String callId;
    private String name;
    private String result;

    public ToolCallResult() {
    }

    public ToolCallResult(String callId, String name, String result) {
        this.callId = callId;
        this.name = name;
        this.result = result;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
