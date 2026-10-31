/**
 * Annotation set describing the script-facing API surface
 * ({@code @ApiModule}, {@code @ApiEvent}, {@code @ApiSince}, ...).
 *
 * <p>Pure annotations with runtime retention and no dependencies: the runtime
 * binding builder and the documentation generator consume the same annotated
 * classes, keeping bindings, generated Markdown and TypeScript definitions in
 * sync from a single source of truth.</p>
 */
package ink.tuanzi.neomacros.doc.api;
