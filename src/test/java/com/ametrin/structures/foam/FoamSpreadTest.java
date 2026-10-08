package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASDataComponents;
import com.ametrin.structures.registry.ASFoamSpreadBehaviors;
import com.ametrin.structures.registry.ASFoamSpreadRestrictions;
import com.ametrin.structures.registry.ASItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// The foam component: its codec, the preset stacks, and the tooltip every foam
/// explains itself with.
@ExtendWith(EphemeralTestServerProvider.class)
class FoamSpreadTest {
    @Test
    void presetsSurviveTheComponentCodec(MinecraftServer server) {
        for (var preset : FoamPresets.ALL) {
            var json = FoamSpread.CODEC.encodeStart(JsonOps.INSTANCE, preset.spread()).getOrThrow();
            assertEquals(preset.spread(), FoamSpread.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), preset.name());
        }
    }

    @Test
    void restrictionsDefaultToNone(MinecraftServer server) {
        var json = JsonParser.parseString("{\"behavior\": {\"type\": \"ametrin_structures:faces\"}}");
        assertEquals(new FoamSpread(FoamSpreadBehaviors.Faces.INSTANCE, List.of()), FoamSpread.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void presetStacksAreTheOneFoamItem(MinecraftServer server) {
        var stack = FoamPresets.ALL.get(1).createStack();
        assertTrue(stack.is(ASItems.FOAM.get()));
        assertEquals(FoamPresets.INDOOR, stack.get(ASDataComponents.FOAM_SPREAD));
        assertEquals("item.ametrin_structures.foam.indoor", key(stack.get(DataComponents.ITEM_NAME)));
        assertEquals(FoamPresets.BASIC, ASItems.FOAM.toStack().get(ASDataComponents.FOAM_SPREAD), "the plain item is basic foam");
    }

    @Test
    void tooltipExplainsTheFillLineByLine(MinecraftServer server) {
        List<Component> lines = tooltip(FoamPresets.INDOOR);
        assertEquals(4, lines.size());
        assertEquals("tooltip.ametrin_structures.foam.fill", key(lines.get(0)));
        assertEquals("foam_spread_behavior.ametrin_structures.faces_and_edges", key(lines.get(1).getSiblings().getFirst()));
        var distance = lines.get(2).getSiblings().getFirst();
        assertEquals("foam_spread_restriction.ametrin_structures.max_distance", key(distance));
        assertEquals(FoamPresets.DEFAULT_MAX_DISTANCE, ((TranslatableContents) distance.getContents()).getArgs()[0]);
        assertEquals("foam_spread_restriction.ametrin_structures.requires_shelter", key(lines.get(3).getSiblings().getFirst()));
    }

    @Test
    void foamWithoutRestrictionsIsFlagged(MinecraftServer server) {
        List<Component> lines = tooltip(new FoamSpread(FoamSpreadBehaviors.Faces.INSTANCE, List.of()));
        assertEquals(3, lines.size());
        assertEquals("tooltip.ametrin_structures.foam.unbounded", key(lines.get(2).getSiblings().getFirst()));
    }

    /// A custom type gets its line from a lang entry alone, so every built-in type has one to show it works.
    @Test
    void everyBuiltInTypeHasATooltipLine() throws Exception {
        JsonObject lang;
        try (var reader = new InputStreamReader(
                FoamSpreadTest.class.getResourceAsStream("/assets/ametrin_structures/lang/en_us.json"), StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (var type : ASFoamSpreadBehaviors.REGISTER.getEntries()) {
            var key = "foam_spread_behavior." + type.getId().toLanguageKey();
            assertTrue(lang.has(key), key);
        }
        for (var type : ASFoamSpreadRestrictions.REGISTER.getEntries()) {
            var key = "foam_spread_restriction." + type.getId().toLanguageKey();
            assertTrue(lang.has(key), key);
        }
    }

    private static List<Component> tooltip(FoamSpread spread) {
        List<Component> lines = new ArrayList<>();
        spread.addToTooltip(Item.TooltipContext.EMPTY, lines::add, TooltipFlag.NORMAL, DataComponentMap.EMPTY);
        return lines;
    }

    private static String key(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents()).getKey();
    }
}
