package priv.dawn.swarm.enums;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/19/18:30
 */

public enum Roles {
    SYSTEM("system"),
    USER("user"),
    TOOL("tool"),
    ASSISTANT("assistant"),
    ;
    public final String value;
    Roles(String value){
        this.value = value;
    }

}
