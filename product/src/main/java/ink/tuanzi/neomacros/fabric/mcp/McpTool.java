package ink.tuanzi.neomacros.fabric.mcp;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Function;

/**
 * One tool exposed through {@code tools/list} and invoked through
 * {@code tools/call}. The handler receives the decoded {@code arguments}
 * object and returns any JSON-mappable value (records, maps, lists,
 * primitives) that becomes the result's single text content; failures the
 * agent should read surface as {@link McpToolError}.
 *
 * @param name        the unique snake_case tool name
 * @param description the agent-facing summary (one or two sentences)
 * @param group       the config group the tool belongs to (logs/state/control/eval)
 * @param inputSchema the JSON Schema (draft 2020-12) of the arguments object
 * @param handler     the invocation
 */
public record McpTool(String name, String description, String group,
                      JsonObject inputSchema, Function<Map<String, Object>, Object> handler) {
}
