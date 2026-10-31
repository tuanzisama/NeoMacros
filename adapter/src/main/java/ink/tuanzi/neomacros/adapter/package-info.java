/**
 * 版本相关适配层 —— 仓库中唯一的版本相关源码目录（架构文档 06）。
 *
 * <p>T-000 仅占位；端口实现自 T-030 起落地。</p>
 *
 * <p>硬性约束（架构文档 08 §1）：</p>
 * <ul>
 *   <li>本目录（及 fabric 入口）是唯一允许 import {@code net.minecraft.*} 与 Fabric API 的源码目录；</li>
 *   <li>core / ports / engine / doc-api 出现这些 import 或 {@code //?} 预处理器即违规
 *      （守卫测试由 T-011 的 ArchUnit 落地）；</li>
 *   <li>端口签名只加不改，加方法必须带 default 实现。</li>
 * </ul>
 */
package ink.tuanzi.neomacros.adapter;
