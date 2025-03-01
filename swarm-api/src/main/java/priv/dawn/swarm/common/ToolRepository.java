package priv.dawn.swarm.common;

import com.google.gson.Gson;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import priv.dawn.swarm.api.CallableFunction;
import priv.dawn.swarm.enums.ParamType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @author Dawn Yang
 * @since 2025/01/19/10:01
 */
public class ToolRepository {

    private final Map<String, ToolFunction> functionMap;

    public ToolRepository() {
        functionMap = new HashMap<>();
    }

    public void register(ToolFunction tool) {
        functionMap.put(tool.getName(), tool);
    }

    public FunctionFactory factory() {
        return new FunctionFactory(this);
    }

    public ToolFunction getTool(String name) {
        return functionMap.get(name);
    }

    public List<String> getNameList() {
        return new ArrayList<>(functionMap.keySet());
    }

    public boolean isEmpty() {
        return functionMap.isEmpty();
    }

    public ToolRepository subRepository(String... names) {
        HashSet<String> subNameSet = new HashSet<>(Arrays.asList(names));
        Map<String, ToolFunction> collect = this.functionMap.entrySet().stream()
                .filter(entry -> subNameSet.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new ToolRepository(collect);
    }

    private ToolRepository(Map<String, ToolFunction> functionMap) {
        this.functionMap = functionMap;
    }

    public static class FunctionFactory {

        private final HashMap<String, Object> params = new HashMap<>();
        private String name;
        private String desc;
        private CallableFunction function;
        private final List<String> requires = new ArrayList<>();
        private boolean isDefine = false;
        private String defineSchema;
        private final ToolRepository repository;

        private FunctionFactory(ToolRepository repository) {
            this.repository = repository;
        }

        public FunctionFactory name(String name) {
            this.name = name;
            return this;
        }

        public FunctionFactory description(String description) {
            this.desc = description;
            return this;
        }

        public FunctionFactory addParam(String name, String description, ParamType type, boolean require) {
            Map<String, String> map = new HashMap<>(4, 1f);
            map.put("description", description);
            map.put("type", type.value);
            this.params.put(name, map);
            if (require) {
                this.requires.add(name);
            }
            return this;
        }

        public FunctionFactory addArrayParam(String name, String description, ParamType itemsType, boolean require) {
            Map<String, Object> map = new HashMap<>(4, 1f);
            map.put("description", description);
            map.put("type", "array");
            HashMap<String, String> items = new HashMap<>(4, 1f);
            items.put("type", itemsType.value);
            map.put("items", items);
            this.params.put(name, map);
            if (require) {
                this.requires.add(name);
            }
            return this;
        }

        /**
         * 自定义 json schema
         * 该方法一旦调用，则 addParam 和 addArrayParam 的内容无效
         * 定义规则：
         * type字段固定为"object"；
         * properties字段描述了入参的名称、数据类型与描述，为 Object 类型，Key 值为入参的名称，Value 值为入参的数据类型与描述；
         * required字段指定哪些参数为必填项，为 Array 类型。
         * 示例：
         * {
         *     "type": "object",
         *     "properties": {
         *         "location": {
         *             "type": "string",
         *             "description": "城市或县区，比如北京市、杭州市、余杭区等。",
         *         }
         *     },
         *     "required": ["location"]
         * }
         *
         * @param jsonSchema 对应 parameters
         */
        public FunctionFactory defineParam(String jsonSchema) {
            this.defineSchema = jsonSchema;
            this.isDefine = true;
            return this;
        }

        public FunctionFactory function(CallableFunction function) {
            this.function = function;
            return this;
        }

        /**
         * TODO 如果构造失败，抛出一个异常
         */
        public void register() {
            if (StringUtils.isBlank(name) || StringUtils.isBlank(desc)) {
                return;
            }
            if (Objects.isNull(function)) {
                return;
            }
            String paramJson = null;
            if (isDefine) {
                paramJson = this.defineSchema;
            } else if (MapUtils.isNotEmpty(params)) {
                HashMap<String,Object> schema = new HashMap<>(4,1f);
                schema.put("type","object");
                schema.put("properties",params);
                schema.put("required",requires);
                paramJson = new Gson().toJson(schema);
            }
            ToolFunction tool = new ToolFunction(name, paramJson, desc, function);
            this.repository.register(tool);
        }
    }

}
