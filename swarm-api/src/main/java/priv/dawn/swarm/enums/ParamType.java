package priv.dawn.swarm.enums;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/03/01/19:57
 */
public enum ParamType {
    STRING("string"),
    INTEGER("integer"),
    BOOLEAN("boolean"),
    NUMBER("number"),
    ;
    public final String value;

    ParamType(String value) {
        this.value = value;
    }
}
