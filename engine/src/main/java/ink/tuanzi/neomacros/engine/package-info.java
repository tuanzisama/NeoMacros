/**
 * engine：脚本引擎封装 —— {@code ScriptEngine} SPI 的 GraalJS 实现（架构文档 03 §1、05 §2）。
 *
 * <p>职责：GraalJS Context 管理、{@code @neomacros/*} ESM specifier 解析、模块缓存与
 * reload 清空、{@code neo} 全局注入、Promise↔事件循环桥、host access 关闭、
 * {@code PolyglotException} → {@code ScriptError} 翻译。由 T-022 落地（风险任务 R1，
 * 允许提前以 spike 分支探索）。</p>
 */
package ink.tuanzi.neomacros.engine;
