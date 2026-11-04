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
    void classSectionsRenderKindWrapsAndTree() throws IOException {
        String world = read(outMd.outDir().resolve("api/world.md"));
        assertTrue(world.contains("## Class WorldHandle"), "class section rendered");
        assertTrue(world.contains("Wraps: `net.minecraft.world.fixture.World`"),
                "wraps badge rendered");
        assertTrue(world.contains("**Class tree**"), "class tree rendered");
        assertTrue(world.contains("- `WorldHandle`"), "tree lists the root class");
        assertTrue(world.contains("  - `RegionHandle`"), "tree nests the subclass");
        assertTrue(world.contains("Extended by: `RegionHandle`"), "parent lists its subclasses");
        assertTrue(world.contains("Extends: `WorldHandle`"), "subclass lists its parent");
        assertTrue(world.contains("## Class WorldInfo"), "data class rendered");
        assertTrue(world.contains("Data · Wraps"), "data kind badge rendered");
        assertFalse(world.contains("## Handle "), "interface-era heading is gone");

        String zh = read(outMd.outDir().resolve("api/world.zh.md"));
        assertTrue(zh.contains("**类树**"), "Chinese class tree label rendered");
        assertTrue(zh.contains("包装：`net.minecraft.world.fixture.World`"), "Chinese wraps badge");
        assertTrue(zh.contains("被继承：`RegionHandle`"), "Chinese extended-by badge");
        assertTrue(zh.contains("继承：`WorldHandle`"), "Chinese extends badge");
    }

    @Test
    void typesPageRenderedWithClassTree() throws IOException {
        String types = read(outMd.outDir().resolve("api/types.md"));
        assertTrue(types.contains("## StandaloneVec"), "standalone types page rendered");
        assertTrue(types.contains("Data"), "data kind badge rendered");
        
        
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
