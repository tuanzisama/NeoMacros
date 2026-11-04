package ink.tuanzi.neomacros.doc.api.fixture;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiExperimental;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.concurrent.CompletableFuture;

/**
 * Fixture exercising every annotation of this jar on one facade, mirroring
 * the layout the runtime binding builder and the documentation generator
 * expect from real API modules (module declaration, nested handle, event
 * records, getter and action members).
 */
@ApiModule(name = "fixture", category = "test")
@ApiSince("0.1.0")
@ApiMC(">=1.20.6")
@ApiPermission("fs")
@ApiExperimental
public class DocApiFixture {

    /** Async action with an explicit script-visible return type. */
    @ApiSince("0.1.0")
    @ApiType("Promise<void>")
    public CompletableFuture<Void> action(@ApiType("string") String arg) {
        return CompletableFuture.completedFuture(null);
    }

    /** Internal helper hidden from the generated documentation. */
    @ApiInternal
    public void hidden() {
    }

    /** Live handle type with a read-only bean getter. */
    @ApiClass(name = "FixtureHandle", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.fixture.World")
    public static final class FixtureHandle {

        /** Display name of the fixture. */
        @ApiReadOnly
        public String getName() {
            return "fixture";
        }
    }

    /** Immutable data type exercising the data kind. */
    @ApiClass(name = "FixtureData", kind = ApiClassKind.DATA)
    public static final class FixtureData {

        /** Arbitrary stored value. */
        @ApiReadOnly
        public String getValue() {
            return "data";
        }
    }

    /** Cancellable event carrying a message payload. */
    @ApiEvent(name = "fixture:marked", cancellable = true)
    public record Marked(String message) {
    }

    /** Plain event using every default the descriptor offers. */
    @ApiEvent(name = "fixture:plain")
    public record Plain() {
    }
}
