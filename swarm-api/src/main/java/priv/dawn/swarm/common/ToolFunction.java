package priv.dawn.swarm.common;

import priv.dawn.swarm.api.CallableFunction;

/**
 * Created with IntelliJ IDEA.
 * Description: 工具函数
 *
 * @author Dawn Yang
 * @since 2025/01/19/9:56
 */

public class ToolFunction {

    private final String name;
    private final String parameterSchema;
    private final String description;

    private final CallableFunction callableFunction;

    public ToolFunction(String name, String parameterSchema, String description, CallableFunction callableFunction) {
        this.name = name;
        this.parameterSchema = parameterSchema;
        this.description = description;
        this.callableFunction = callableFunction;
    }

    public String getName() {
        return name;
    }

    public String getParameterSchema() {
        return parameterSchema;
    }

    public String getDescription() {
        return description;
    }

    public CallableFunction getCallableFunction() {
        return callableFunction;
    }
}
