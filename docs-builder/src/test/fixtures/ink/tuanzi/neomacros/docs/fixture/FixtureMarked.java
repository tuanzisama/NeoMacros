package ink.tuanzi.neomacros.docs.fixture;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Fixture event descriptor with complete bilingual documentation: a
 * cancellable event carrying a message payload.
 *
 * @zh 练习用的可取消事件描述符，载荷为一条消息。
 */
@ApiSince("0.1.0")
@ApiEvent(name = "fixture:marked", cancellable = true)
public record FixtureMarked(String message, int severity) {
}
