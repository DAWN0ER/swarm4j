package priv.dawn.swarm.common;

import priv.dawn.swarm.enums.ToolChoices;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/18/19:48
 */

public class Agent {

    private final String name;
    private final String model;
    private final String instructions;
    private final ToolRepository functions;
    private final String toolChoice;
    private final boolean parallelToolCalls;

    private Agent(AgentBuilder builder) {
        this.name = builder.name;
        this.model = builder.model;
        this.instructions = builder.instructions;
        this.functions = builder.functions;
        this.toolChoice = builder.toolChoice;
        this.parallelToolCalls = builder.parallelToolCalls;
    }

    public String getName() {
        return name;
    }

    public String getModel() {
        return model;
    }

    public String getInstructions() {
        return instructions;
    }

    public ToolRepository getFunctions() {
        return functions;
    }

    public String getToolChoice() {
        return toolChoice;
    }

    public boolean isParallelToolCalls() {
        return parallelToolCalls;
    }

    public static AgentBuilder builder(){
        return new AgentBuilder();
    }

    public static class AgentBuilder{

        private String name = "Agent";
        private String model = null;
        private String instructions = "You are a helpful agent.";
        private ToolRepository functions = null;
        private String toolChoice = ToolChoices.AUTO.value;
        private boolean parallelToolCalls = true;

        public AgentBuilder name(String name) {
            this.name = name;
            return this;
        }

        public AgentBuilder model(String model) {
            this.model = model;
            return this;
        }

        public AgentBuilder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public AgentBuilder functions(ToolRepository functions) {
            this.functions = functions;
            return this;
        }

        public AgentBuilder toolChoice(String toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public AgentBuilder parallelToolCalls(boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Agent build() {
            return new Agent(this);
        }
    }
}
