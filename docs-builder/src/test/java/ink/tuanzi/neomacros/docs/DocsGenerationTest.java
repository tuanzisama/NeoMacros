package ink.tuanzi.neomacros.docs;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snapshot regression over the fixture API, in the spirit of the JsMacros
 * testTsRenderer setup: generated artifacts are compared byte-for-byte
 * against committed baselines, and warning/fallback behavior is asserted
 * explicitly. Update the baselines deliberately (inspect the diff first!)
 * when the renderers change.
 */
class DocsGenerationTest {

    // Fixed directory (not @TempDir) so generated artifacts stay inspectable
    // and can be promoted to snapshots after review; cleaned by `clean`.
    static Path outDir = Path.of("build", "manual-gen");

    @BeforeAll
    static void generateAll() throws IOException {
        DocsGenerationTest.outMd = GenerationHarness.generate(MDDoclet.class, outDir.resolve("md"));
        DocsGenerationTest.outTs = GenerationHarness.generate(TSDoclet.class, outDir.resolve("ts"));
    }

    static GenerationHarness.Run outMd;
    static GenerationHarness.Run outTs;

    @Test
    void generationSucceedsWithoutErrors() {
        assertTrue(outMd.ok(), () -> "MD generation failed: " + outMd.errors());
        assertTrue(outTs.ok(), () -> "TS generation failed: " + outTs.errors());
    }

    @Test
    void englishPagesMatchSnapshots() throws IOException {
        for (String module : new String[]{"world", "partial"}) {
            assertEquals(readSnapshot("api/" + module + ".md"),
                    read(outMd.outDir().resolve("api/" + module + ".md")),
                    "English page for " + module + " drifted from the committed snapshot");
        }
    }

    @Test
    void chinesePagesMatchSnapshots() throws IOException {
        for (String module : new String[]{"world", "partial"}) {
            assertEquals(readSnapshot("api/" + module + ".zh.md"),
                    read(outMd.outDir().resolve("api/" + module + ".zh.md")),
                    "Chinese page for " + module + " drifted from the committed snapshot");
        }
    }

    @Test
    void warningsListMatchesSnapshot() throws IOException {
        String warnings = read(outMd.outDir().resolve("api/" + DocletSupport.WARNINGS_FILE));
        assertEquals(readSnapshot("api/" + DocletSupport.WARNINGS_FILE), warnings);
        assertTrue(warnings.contains("partial: module doc has no @zh body"));
        assertTrue(warnings.contains("partial.untagged: method has no @zh docs"));
        assertTrue(warnings.contains("partial.untagged: missing @ApiSince"));
        assertTrue(warnings.contains("partial: module is missing @ApiSince"));
    }

    @Test
    void chinesePageFallsBackToEnglishPerMember() throws IOException {
        String page = read(outMd.outDir().resolve("api/partial.zh.md"));
        assertTrue(page.contains("Returns a value whose member lacks translation and version annotation"),
                "untagged member falls back to the English text");
        assertTrue(page.contains("返回完整文档化的值。"),
                "translated member keeps its Chinese text");
    }

    @Test
    void badgesAndBadgesOnlyRenderedWhereAnnotated() throws IOException {
        String world = read(outMd.outDir().resolve("api/world.md"));
        assertTrue(world.contains("Experimental"), "experimental badge rendered");
        assertTrue(world.contains("Permission: `command`"), "permission badge rendered");
        assertTrue(world.contains("MC: >=1.20.6"), "MC range badge rendered");
        assertFalse(world.contains("hiddenInternal"), "internal members stay hidden");
        assertTrue(world.contains("(read-only)"), "read-only badge rendered");
    }

    @Test
    void zhExampleRenderedOnlyInChinesePage() throws IOException {
        String zh = read(outMd.outDir().resolve("api/world.zh.md"));
        String en = read(outMd.outDir().resolve("api/world.md"));
        assertTrue(zh.contains("world.mark(new Pos3d(1, 64, 1));"), "example in the Chinese page");
        assertFalse(en.contains("**Example**"), "example stays out of the English page");
    }

    @Test
    void modulePagesLinkToClassPages() throws IOException {
        String world = read(outMd.outDir().resolve("api/world.md"));
        assertTrue(world.contains("**Classes**"), "classes link section rendered");
        assertTrue(world.contains("[`WorldHandle`](classes/WorldHandle.html)"),
                "class link rendered");
        assertFalse(world.contains("## Class WorldHandle"), "class sections moved to their own pages");
    }

    @Test
    void classPagesRenderKindWrapsAndInheritance() throws IOException {
        String handle = read(outMd.outDir().resolve("api/classes/WorldHandle.md"));
        assertTrue(handle.contains("# WorldHandle"), "class page rendered");
        assertTrue(handle.contains("Handle · Wraps: `net.minecraft.world.fixture.World`"),
                "kind and wraps meta rendered");
        assertTrue(handle.contains("Extended by: [`RegionHandle`](RegionHandle.html)"),
                "parent lists its subclasses");
        String region = read(outMd.outDir().resolve("api/classes/RegionHandle.md"));
        assertTrue(region.contains("Extends: [`WorldHandle`](WorldHandle.html)"),
                "subclass lists its parent");
        String info = read(outMd.outDir().resolve("api/classes/WorldInfo.md"));
        assertTrue(info.contains("Data"), "data kind badge rendered");
        String pos = read(outMd.outDir().resolve("api/classes/FixturePos.md"));
        assertTrue(pos.contains("Record type referenced in module signatures."),
                "referenced record type page rendered");
    }

    @Test
    void classPagesKeepEnglishTermsOnChinesePages() throws IOException {
        String zh = read(outMd.outDir().resolve("api/classes/WorldHandle.zh.md"));
        assertTrue(zh.contains("Handle · Wraps: `net.minecraft.world.fixture.World`"),
                "kind and wraps terms stay English on the Chinese page");
        assertTrue(zh.contains("Extended by: [`RegionHandle`](RegionHandle.html)"),
                "inheritance terms stay English on the Chinese page");
        assertTrue(zh.contains("fixture 世界的活引用句柄"), "translated body kept");
    }

    @Test
    void standaloneClassPageRendered() throws IOException {
        String vec = read(outMd.outDir().resolve("api/classes/StandaloneVec.md"));
        assertTrue(vec.contains("# StandaloneVec"), "standalone class page rendered");
        assertTrue(vec.contains("Data"), "data kind badge rendered");
    }

    @Test
    void eventPagesRendered() throws IOException {
        String marked = read(outMd.outDir().resolve("api/events/fixture-marked.md"));
        assertTrue(marked.contains("# fixture:marked"), "event page rendered");
        assertTrue(marked.contains("Cancellable"), "cancellable badge rendered");
        assertTrue(marked.contains("`message`"), "payload fields rendered");
        String zh = read(outMd.outDir().resolve("api/events/fixture-marked.zh.md"));
        assertTrue(zh.contains("Cancellable"), "cancellable badge stays English on the Chinese page");
        assertTrue(zh.contains("练习用的可取消事件描述符"), "translated body kept");
    }

    @Test
    void tsDefinitionsEmitClassDeclarations() throws IOException {
        String ts = read(outTs.outDir().resolve("types/neomacros.d.ts"));
        assertTrue(ts.contains("export class WorldHandle"), "classes emitted as class declarations");
        assertFalse(ts.contains("export interface WorldHandle"), "class form replaces interface form");
        assertTrue(ts.contains("export class RegionHandle extends WorldHandle"),
                "extends chain rendered");
        assertTrue(ts.contains("export class WorldInfo"), "data kind is a class too");
    }

    @Test
    void tsDefinitionsMatchSnapshot() throws IOException {
        assertEquals(readSnapshot("types/neomacros.d.ts"),
                read(outTs.outDir().resolve("types/neomacros.d.ts")),
                "TypeScript definitions drifted from the committed snapshot");
    }

    @Test
    void tsDefinitionsOmitGlobalOnlyModule() throws IOException {
        String ts = read(outTs.outDir().resolve("types/neomacros.d.ts"));
        assertTrue(ts.contains("declare module '@neomacros/partial'"), "global=false still a module");
        assertFalse(ts.contains("partial: import("), "global=false stays out of neo");
        assertTrue(ts.contains("declare const neo"), "global aggregate present");
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static String readSnapshot(String relative) throws IOException {
        return read(Paths.get("src", "test", "snapshots").resolve(relative));
    }
}
