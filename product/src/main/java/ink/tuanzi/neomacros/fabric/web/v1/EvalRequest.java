package ink.tuanzi.neomacros.fabric.web.v1;

/** DevTools console evaluation request body. */
public record EvalRequest(String target, String expression) {
}
