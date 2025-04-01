package priv.dawn.test.swarm;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;
import priv.dawn.swarm.api.AgentClient;
import priv.dawn.swarm.common.Agent;
import priv.dawn.swarm.common.AgentMessage;
import priv.dawn.swarm.common.ToolRepository;
import priv.dawn.swarm.domain.DashScopeClient;
import priv.dawn.swarm.enums.ParamType;
import priv.dawn.swarm.enums.Roles;
import priv.dawn.swarm.enums.ToolChoices;

import java.util.Objects;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/04/01/12:47
 */
public class DemoTest {

    @Test
    public void test(){
        String key = System.getenv("KEY");
//        AgentClient client = new OpenAIClient(key,"https://dashscope.aliyuncs.com/compatible-mode/v1/");
        AgentClient client = new DashScopeClient(key,"https://dashscope.aliyuncs.com/compatible-mode/v1/");

        ToolRepository fs = new ToolRepository();
        fs.factory().name("get_weather").description("根据城市（必需）和时间（必需）查询当地的天气情况。")
                .addParam("city","查询天气的城市名。", ParamType.STRING,true)
                .addParam("time","查询天气的时间，格式为 yyyy-mm-dd。",ParamType.STRING,true)
                .function(str->{
                    System.out.println("strParam = " + str);
                    JsonObject object = new Gson().fromJson(str, JsonObject.class);
                    JsonElement element = object.get("time");
                    if(Objects.isNull(element) || "".equals(element.getAsString())){
                        return "缺失时间信息。";
                    }
                    return "晴朗无云，气温24摄氏度。";
                })
                .register();
        fs.factory().name("get_time").description("获取当前时间。")
                .function(str->{
                    System.out.println("strVoid = " + str);
                    return "2025-04-01";
                })
                .register();

        Agent agent = Agent.builder()
                .model("qwen-max")
                .instructions("你是一只可爱的猫娘，说话都会带喵喵叫。如果需要了解天气，先使用get_time拿到今天时间，再使用get_weather。")
                .toolChoice(ToolChoices.AUTO.value)
//                .parallelToolCalls(false)
                .functions(fs)
                .build();
        AgentMessage message = new AgentMessage();
        message.setRole(Roles.USER.value);
        message.setContent("成都天气怎么样？");

        System.out.println(fs.getTool("get_weather").getParameters());
//        List<AgentMessage> run = client.run(agent, Arrays.asList(message), 10);
//        System.out.println("run = " + new Gson().toJson(run));

    }

}
