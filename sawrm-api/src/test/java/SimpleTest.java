import org.junit.Test;
import priv.dawn.swarm.common.ToolFunction;
import priv.dawn.swarm.common.ToolRepository;
import priv.dawn.swarm.enums.ParamType;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/03/01/19:42
 */

public class SimpleTest {

    @Test
    public void test01() {
        ToolRepository repository = new ToolRepository();
        repository.factory()
                .name("Fn_1")
                .description("这是第一个函数")
                .function(str -> str + "!!")
                .addParam("P1", "第一个参数", ParamType.STRING, true)
                .addArrayParam("PA1", "数组参数", ParamType.INTEGER, true)
                .register();
        ToolFunction tool = repository.getTool("Fn_1");
        System.out.println(tool.getParameters());
    }

}
