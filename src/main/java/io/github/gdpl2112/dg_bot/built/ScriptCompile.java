package io.github.gdpl2112.dg_bot.built;

import lombok.Getter;

import javax.script.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class ScriptCompile {
    private CompiledScript script;
    private ScriptEngine engine;

    public ScriptCompile(String scriptText) {
        this.script = initScript(scriptText, null);
    }

    /**
     * @param scriptText js脚本内容
     * @param initParams 在编译时, 初始化时传入脚本的参数
     */
    public ScriptCompile(String scriptText, Map<String, Object> initParams) {
        this.script = initScript(scriptText, initParams);
    }

    public CompiledScript initScript(String scriptText, Map<String, Object> initParams) {
        CompiledScript script = null;
        ScriptEngine engine = new ScriptEngineManager().getEngineByExtension("js");
        // 检查JavaScript引擎是否可用
        if (engine == null) {
            throw new RuntimeException("无法获取JavaScript引擎，请确认Java环境支持JavaScript");
        }
        if (initParams != null && !initParams.isEmpty()) {
            initParams.forEach(engine::put);
        }
        extractFunctionNames(scriptText);
        this.engine = engine;
        if (engine instanceof Compilable) {
            try {
                script = ((Compilable) engine).compile(scriptText);
            } catch (ScriptException e) {
                throw new RuntimeException("脚本编译失败: " + e.getMessage(), e);
            }
        }
        return script;
    }

    /**
     * 执行脚本
     *
     * @param bindingsMap 本次执行时传入的参数
     * @return
     * @throws ScriptException
     */
    public Object execute(Map<String, Object> bindingsMap) throws ScriptException {
        if (script == null) {
            throw new RuntimeException("脚本未成功编译，无法执行");
        }
        if (bindingsMap != null && !bindingsMap.isEmpty()) {
            Bindings bindings = new SimpleBindings();
            for (Map.Entry<String, Object> entry : bindingsMap.entrySet()) {
                bindings.put(entry.getKey(), entry.getValue());
            }
            return script.eval(bindings);
        } else {
            return script.eval();
        }
    }

    public Object execute() throws ScriptException {
        return execute(null);
    }

    /**
     * 调用脚本中的某个函数
     *
     * @param bindingsMap 本次执行传入的参数
     * @param fucName     函数名
     * @param args        函数参数,可以有多个参数
     * @return
     * @throws Exception
     */
    public Object executeFuc(Map<String, Object> bindingsMap, String fucName, Object... args) throws Exception {
        if (script == null) {
            throw new RuntimeException("脚本未成功编译，无法执行函数");
        }
        execute(bindingsMap);
        Invocable inv2 = (Invocable) engine;
        return inv2.invokeFunction(fucName, args);
    }

    public Object executeFuc(String fucName, Object... args) throws Exception {
        return executeFuc(null, fucName, args);
    }
    // 存储所有提取的函数名
    private Set<String> functionNames = new HashSet<>();

    // 新增：通过正则表达式提取函数名
    private void extractFunctionNames(String scriptText) {
        // 匹配函数声明：function funcName() {...}
        Pattern pattern = Pattern.compile("function\\s+([a-zA-Z_$][\\w$]*)\\s*\\(");
        Matcher matcher = pattern.matcher(scriptText);
        while (matcher.find()) {
            functionNames.add(matcher.group(1));
        }

        // 匹配函数表达式：var funcName = function() {...}
        pattern = Pattern.compile("var\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*function\\s*\\(");
        matcher = pattern.matcher(scriptText);
        while (matcher.find()) {
            functionNames.add(matcher.group(1));
        }

        // 匹配箭头函数：var funcName = () => {...}
        pattern = Pattern.compile("var\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*\\([^)]*\\)\\s*=>");
        matcher = pattern.matcher(scriptText);
        while (matcher.find()) {
            functionNames.add(matcher.group(1));
        }
    }

    // 新增：检测函数是否存在
    public boolean hasFunction(String functionName) {
        return functionNames.contains(functionName);
    }
}