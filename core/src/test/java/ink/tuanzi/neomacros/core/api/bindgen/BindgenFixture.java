package ink.tuanzi.neomacros.core.api.bindgen;

import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixture module class exercising the full annotation set for binding extraction:
 * live getter property, sync and async functions, a permission-gated member,
 * a node-filtered member, and an internal member.
 */
@ApiModule(name = "fixture", category = "test")
@ApiSince("0.1.0")
public final class BindgenFixture {

    private final AtomicInteger reads = new AtomicInteger();

    /** Live read counter; increments on every property access. @zh 活计数器属性。 */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public int getReads() {
        return reads.incrementAndGet();
    }

    /** Sync greeting function. @zh 同步问候函数。 */
    @ApiSince("0.1.0")
    public String greet(String name) {
        return "hi " + name;
    }

    /** Async fetch function mapping to a promise. @zh 映射为 Promise 的异步函数。 */
    @ApiSince("0.1.0")
    @ApiType("Promise<string>")
    public CompletableFuture<String> fetch(String what) {
        return CompletableFuture.completedFuture("got:" + what);
    }

    /** Permission-gated member. @zh 权限门控成员。 */
    @ApiSince("0.1.0")
    @ApiPermission("fs")
    public String secret() {
        return "s3cret";
    }

    /** Member available only on modern nodes. @zh 仅新节点可用的成员。 */
    @ApiSince("0.1.0")
    @ApiMC(">=1.20.6")
    public String futureOnly() {
        return "modern";
    }

    /** Internal member kept out of generated documentation. @zh 内部成员。 */
    @ApiInternal
    public void hiddenInternal() {
    }
}
