package ink.tuanzi.neomacros.fabric.mcp;

/**
 * A tool execution that failed for a reason the agent should see as the
 * result payload (an {@code isError} tool result, not a JSON-RPC protocol
 * error): the client is not inside a world, a named script does not exist,
 * a required argument is missing or malformed.
 */
public final class McpToolError extends RuntimeException {

    /** Creates the error with an agent-readable message. */
    public McpToolError(String message) {
        super(message);
    }
}
