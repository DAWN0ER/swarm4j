package priv.dawn.swarm.common;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/19/17:01
 */

public class ToolCallRequest {

    private String callId;
    private String name;
    private String jsonParam;

    public ToolCallRequest() {
    }

    public ToolCallRequest(String callId, String name, String jsonParam) {
        this.callId = callId;
        this.name = name;
        this.jsonParam = jsonParam;
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

    public String getJsonParam() {
        return jsonParam;
    }

    public void setJsonParam(String jsonParam) {
        this.jsonParam = jsonParam;
    }

}
