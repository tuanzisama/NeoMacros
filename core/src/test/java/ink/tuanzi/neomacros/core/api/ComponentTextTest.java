package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.types.Component;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.TextComponent;
import ink.tuanzi.neomacros.core.util.JsonLite;
import ink.tuanzi.neomacros.port.model.TextSegmentData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Component/TextComponent contract: builder chaining, build
 * non-destructiveness, the three-form append, tree inheritance at flatten
 * time, and the vanilla-tree JSON face.
 */
class ComponentTextTest {

    @Test
    void builderChainStylesTheRoot() {
        TextComponent built = new Component("警告：").color("red").bold(true).build();
        TextSegmentData segment = built.flatten().get(0);
        assertEquals("警告：", segment.text());
        assertEquals("red", segment.color());
        assertEquals(Boolean.TRUE, segment.bold());
        assertEquals("警告：", built.getPlain());
    }

    @Test
    void buildIsNonDestructive() {
        Component draft = new Component("a");
        TextComponent first = draft.build();
        draft.color("red").append("b");
        TextComponent second = draft.build();
        assertNull(first.flatten().get(0).color(), "first build predates the color call");
        assertEquals("red", second.flatten().get(0).color());
        assertEquals("ab", second.getPlain());
        assertNotSame(first, second);
    }

    @Test
    void appendAcceptsDraftValueAndString() {
        Component child = new Component("b").italic(true);
        TextComponent value = new Component("c").bold(false).build();
        TextComponent built = new Component("a")
                .append(child)
                .append(value)
                .append("d")
                .build();
        List<TextSegmentData> flat = built.flatten();
        assertEquals(4, flat.size());
        assertEquals("a", flat.get(0).text());
        assertEquals("b", flat.get(1).text());
        assertTrue(flat.get(1).italic());
        assertEquals("c", flat.get(2).text());
        assertEquals(Boolean.FALSE, flat.get(2).bold(), "explicit false survives the build");
        assertEquals("d", flat.get(3).text());
        assertNull(flat.get(3).color());
        assertEquals("abcd", built.getPlain());
    }

    @Test
    void appendRejectsForeignTypes() {
        Component draft = new Component("a");
        assertThrows(IllegalArgumentException.class, () -> draft.append(42));
        assertThrows(IllegalArgumentException.class, () -> draft.append(Map.of("x", 1)));
    }

    @Test
    void childrenInheritParentStylingAtFlattenTime() {
        TextComponent built = new Component("root")
                .color("red")
                .clickCommand("/home")
                .hover("tip")
                .append(new Component("child"))
                .append(new Component("over").color("#00ff00"))
                .build();
        List<TextSegmentData> flat = built.flatten();
        assertEquals("red", flat.get(0).color());
        assertEquals("red", flat.get(1).color(), "child inherits the parent color");
        assertEquals("#00ff00", flat.get(2).color(), "explicit child color wins");
        assertEquals("/home", flat.get(1).clickAction().equals("run_command") ? "/home" : null);
        assertEquals("run_command", flat.get(2).clickAction(), "click inherits to children");
        assertEquals("tip", flat.get(1).hoverText(), "hover inherits to children");
    }

    @Test
    void fromMapsTheNeutralTreeDirectly() {
        TextComponent mapped = TextComponent.from(
                new ink.tuanzi.neomacros.port.model.ChatComponent("root", "red",
                        true, null, null, null, null,
                        List.of(new ink.tuanzi.neomacros.port.model.ChatComponent("leaf",
                                null, null, null, null, null, null, List.of()))));
        assertEquals("rootleaf", mapped.getPlain());
        assertEquals(1, mapped.getChildren().size());
        List<TextSegmentData> flat = mapped.flatten();
        assertEquals(2, flat.size());
        assertEquals("red", flat.get(0).color());
        assertEquals("red", flat.get(1).color(), "child inherits at flatten time");
    }

    @Test
    void jsonFaceRendersTheVanillaTree() {
        TextComponent built = new Component("A")
                .color("red")
                .hover("tip")
                .clickCopyToClipboard("A")
                .append(new Component("B").italic(true))
                .build();
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) JsonLite.parse(built.toJSON());
        assertEquals("A", view.get("text"));
        assertEquals("red", view.get("color"));
        @SuppressWarnings("unchecked")
        Map<String, Object> hover = (Map<String, Object>) view.get("hoverEvent");
        assertEquals("show_text", hover.get("action"));
        assertEquals("tip", hover.get("contents"));
        @SuppressWarnings("unchecked")
        Map<String, Object> click = (Map<String, Object>) view.get("clickEvent");
        assertEquals("copy_to_clipboard", click.get("action"));
        assertEquals("A", click.get("value"));
        @SuppressWarnings("unchecked")
        List<Object> extra = (List<Object>) view.get("extra");
        assertEquals(1, extra.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> child = (Map<String, Object>) extra.get(0);
        assertEquals("B", child.get("text"));
        assertEquals(Boolean.TRUE, child.get("italic"));
        assertFalse(child.containsKey("color"), "unset keys are omitted");
    }

    @Test
    void jsonFaceOmitsUnsetKeysAndEmptyExtras() {
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) JsonLite.parse(
                new Component("plain").build().toJSON());
        assertEquals(Map.of("text", "plain"), view);
    }

    @Test
    void hoverMutualExclusionLastWins() {
        TextComponent built = new Component("x")
                .hoverItem("diamond")
                .hover("text wins")
                .build();
        assertNull(built.flatten().get(0).hoverItem());
        assertEquals("text wins", built.flatten().get(0).hoverText());
        TextComponent built2 = new Component("x")
                .hover("text first")
                .hoverItem("diamond")
                .build();
        assertNull(built2.flatten().get(0).hoverText());
        assertEquals("minecraft:diamond", built2.flatten().get(0).hoverItem().id());
    }

    @Test
    void clickFourActionsMapToVanillaNames() {
        TextComponent built = new Component("x")
                .clickUrl("https://example.com")
                .build();
        assertEquals("open_url", built.flatten().get(0).clickAction());
        assertEquals("https://example.com", built.flatten().get(0).clickValue());
        assertEquals("run_command",
                new Component("x").clickCommand("/home").build().flatten().get(0).clickAction());
        assertEquals("suggest_command",
                new Component("x").clickSuggestCommand("/home").build().flatten().get(0).clickAction());
        assertEquals("copy_to_clipboard",
                new Component("x").clickCopyToClipboard("y").build().flatten().get(0).clickAction());
    }

    @Test
    void hoverItemAcceptsIdAndStack() {
        TextComponent byId = new Component("x").hoverItem("diamond").build();
        assertEquals("minecraft:diamond", byId.flatten().get(0).hoverItem().id());
        assertEquals(1, byId.flatten().get(0).hoverItem().count());
        ItemStack stack = ItemStack.of(new ink.tuanzi.neomacros.port.model.ItemStackData(
                "minecraft:diamond", 3, "x"));
        TextComponent byStack = new Component("x").hoverItem(stack).build();
        assertEquals("minecraft:diamond", byStack.flatten().get(0).hoverItem().id());
        assertEquals(3, byStack.flatten().get(0).hoverItem().count());
    }

    @Test
    void copyFromSeedsContentStyleAndChildren() {
        TextComponent base = new Component("b").italic(true).append("c").build();
        TextComponent built = new Component("a")
                .copyFrom(base)
                .color("red")
                .build();
        List<TextSegmentData> flat = built.flatten();
        assertEquals(2, flat.size(), "copied children ride along");
        assertEquals("b", flat.get(0).text(), "copyFrom replaces the draft content");
        assertTrue(flat.get(0).italic(), "own style carries over");
        assertEquals("red", flat.get(0).color(), "chained call overrides");
        assertEquals("c", flat.get(1).text());
        assertEquals("red", flat.get(1).color(), "children inherit the override");
        assertEquals("bc", built.getPlain());
    }

    @Test
    void constructRequiresText() {
        assertEquals("hi", ((Component) new Component().construct(new Object[]{"hi"})).build()
                .getPlain());
        assertThrows(IllegalArgumentException.class, () -> new Component().construct(new Object[0]));
    }

    @Test
    void valueFaceIsReadOnly() {
        TextComponent built = new Component("a").append("b").build();
        assertEquals(1, built.getChildren().size());
        assertThrows(UnsupportedOperationException.class,
                () -> built.getChildren().add(TextComponent.of("x")));
        assertEquals("a", built.getContent());
    }
}
