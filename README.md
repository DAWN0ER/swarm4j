# swarm4j


基于 openai/swarm 复刻的实验性智能体框架项目，内部接入 openai 和 DashScope JDK 调用。

用 Java 复刻的原因：Python 无法做到多个智能体多线程并发调用。因为现在 AI 基本都是调用云平台（比如 OpenAI 和阿里云的百炼平台），可以多并发调用，希望通过 JAVA 解决这个问题。

## 快速开始

示例代码：简单构建智能体角色并调用
```java
class Demo{

    public static void main(String[] args) {

        String key = System.getenv("KEY");
        // 两种客户端实现任选其一
        // AgentClient client = new OpenAIClient(key,"https://dashscope.aliyuncs.com/compatible-mode/v1/");
        AgentClient client = new DashScopeClient(key,"https://dashscope.aliyuncs.com/compatible-mode/v1/");
        
        Agent agent = Agent.builder()
                .model("qwen-max")
                .instructions("你是一只可爱的猫娘，说话都会带喵喵叫。")
                .build();
        AgentMessage message = new AgentMessage();
        message.setRole(Roles.USER.value);
        message.setContent("你是谁？");
        List<AgentMessage> result = client.run(agent, Arrays.asList(message), 10);
    }
    
}
```

### 本地方法调用

提供一个方法仓库实例用于管理本地方法，首先将本地方法注册到方法仓库，然后将仓库注册到Agent里就可以。

本地方法的核心执行逻辑为`priv.dawn.swarm.api.CallableFunction`，接收一个调用参数的 json 字符串，返回一个结果的序列化字符串。

```java
class Demo{

    public static void main(String[] args) {

        String key = System.getenv("KEY");
        // 两种客户端实现任选其一
        // AgentClient client = new OpenAIClient(key,"https://dashscope.aliyuncs.com/compatible-mode/v1/");
        AgentClient client = new DashScopeClient(key,"https://dashscope.aliyuncs.com/compatible-mode/v1/");

        ToolRepository toolRepository = new ToolRepository();
        // 定义并注册有参方法
        toolRepository.factory()
                .name("get_weather")
                .description("如果需要查询天气，使用这个函数，根据城市（必需）和时间（必需）查询当地的天气情况。")
                .addParam("city","查询天气的城市名。", ParamType.STRING,true)
                .addParam("time","查询天气的时间，格式为 yyyy-mm-dd。",ParamType.STRING,true)
                .function(str->{
                    System.out.println("strParam = " + str);
                    return "晴朗无云，气温24摄氏度。";
                })
                .register();
        // 定义并注册无参方法
        toolRepository.factory()
                .name("get_time")
                .description("如果需要得知当前时间，使用这个方法。")
                .function(str->{
                    System.out.println("strVoid = " + str);
                    return "2025-04-01";
                })
                .register();

        Agent agent = Agent.builder()
                .model("qwen-max")
                .instructions("你是一只可爱的猫娘，说话都会带喵喵叫。")
                .toolChoice(ToolChoices.AUTO.value)
                .parallelToolCalls(true)
                // 本地工具注册到 Agent
                .functions(toolRepository)
                .build();
        
        AgentMessage message = new AgentMessage();
        message.setRole(Roles.USER.value);
        message.setContent("成都天气怎么样？");
        List<AgentMessage> run = client.run(agent, Arrays.asList(message), 10);
    }
    
}
```

### 流式调用

```java
class Demo{

    public static void main(String[] args) {

        String key = System.getenv("KEY");
        // 两种客户端实现任选其一
        // AgentClient client = new OpenAIClient(key,"https://dashscope.aliyuncs.com/compatible-mode/v1/");
        AgentClient client = new DashScopeClient(key,"https://dashscope.aliyuncs.com/compatible-mode/v1/");
        
        Agent agent = Agent.builder()
                .model("qwen-max")
                .instructions("你是一只可爱的猫娘，说话都会带喵喵叫。")
                .build();
        AgentMessage message = new AgentMessage();
        message.setRole(Roles.USER.value);
        message.setContent("你是谁？");
        Flowable<AgentStreamMessage> result = client.streamRun(agent, Arrays.asList(message), 10);
        result.blockingForEach(chunk-> System.out.println(chunk.getMessage().getContent()));
    }
    
}
```
