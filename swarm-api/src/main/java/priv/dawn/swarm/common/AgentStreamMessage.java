package priv.dawn.swarm.common;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/18/16:58
 */

public class AgentStreamMessage {

    private int msgIndex;
    private AgentMessage message;

    public AgentStreamMessage() {
    }

    public AgentStreamMessage(int msgIndex, AgentMessage message) {
        this.msgIndex = msgIndex;
        this.message = message;
    }

    public int getMsgIndex() {
        return msgIndex;
    }

    public void setMsgIndex(int msgIndex) {
        this.msgIndex = msgIndex;
    }

    public AgentMessage getMessage() {
        return message;
    }

    public void setMessage(AgentMessage message) {
        this.message = message;
    }
}
