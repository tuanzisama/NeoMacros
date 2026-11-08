package ink.tuanzi.neomacros.fabric.mcp.tools;

import com.google.gson.JsonObject;
import ink.tuanzi.neomacros.fabric.mcp.McpTool;
import ink.tuanzi.neomacros.fabric.mcp.McpToolError;
import ink.tuanzi.neomacros.fabric.web.PanelBackend;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.argString;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.objectSchema;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.stringProperty;

/**
 * The evaluation tool: one JavaScript expression in the shared DevTools
 * session (or inside a named script's context), the same chain the web
 * panel console uses. Guest-level failures come back as structured error
 * fields, not as tool failures, so the agent can iterate on its code.
 */
public final class EvalTools {

    private final PanelBackend backend;

    /** Creates the tool over the panel backend. */
    public EvalTools(PanelBackend backend) {
        this.backend = backend;
    }

    /** The tool of this group. */
    public List<McpTool> tools() {
        return List.of(evalScript());
    }

    private McpTool evalScript() {
        JsonObject properties = new JsonObject();
        properties.add("expression", stringProperty(
                "The JavaScript expression (top-level await is available)"));
        properties.add("target", stringProperty(
                "Optional script id to evaluate inside its context (category/name);"
                        + " omit for the shared devtools session"));
        JsonObject schema = objectSchema(properties, "expression");
        return new McpTool("eval_script",
                "Evaluates JavaScript against the live scripting engine with full"
                        + " access to the neo.* API — the most powerful way to inspect or"
                        + " drive anything the scripting surface covers.",
                "eval", schema, args -> {
                    String expression = argString(args, "expression");
                    if (expression.isBlank()) {
                        throw new McpToolError("'expression' must not be empty");
                    }
                    String target = argString(args, "target", "devtools");
                    PanelBackend.EvalExecution execution =
                            backend.evaluateConsole(target, expression);
                    var outcome = execution.outcome();
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("target", execution.macro());
                    out.put("ok", !outcome.threw());
                    out.put("durationMs", outcome.durationMs());
                    if (outcome.threw()) {
                        out.put("errorName", outcome.errorName());
                        out.put("errorMessage", outcome.errorMessage());
                        out.put("errorStack", outcome.errorStack());
                    } else {
                        out.put("value", outcome.value());
                    }
                    return out;
                });
    }
}
