package priv.dawn.swarm.enums;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/19/21:05
 */
public enum ToolChoices {
    AUTO("auto"),
    NONE("none"),
    REQUIRED("required"),
    ;

    public final String value;
    ToolChoices(String value) {
        this.value = value;
    }
}
