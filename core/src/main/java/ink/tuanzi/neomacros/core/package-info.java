/**
 * core：版本无关、MC 无关的引擎核心（架构文档 03）。
 *
 * <p>ScriptRuntime 枢纽、事件框架、脚本生命周期、线程模型（每 context 事件循环 +
 * SyncWatchdog）、权限信任门、配置/存储、脚本 API 门面（文档真源）。</p>
 *
 * <p>硬性约束（架构文档 08 §1 规则 1）：本模块禁止 import {@code net.minecraft.*}
 * 与 Fabric API，禁止出现 {@code //?} 预处理器 —— 违规即架构腐蚀，由 T-011 的
 * ArchUnit 守卫测试强制。</p>
 */
package ink.tuanzi.neomacros.core;
