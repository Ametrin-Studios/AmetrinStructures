package com.ametrin.structures.debug;

import com.ametrin.structures.structure.ExtendedStructureSettings;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.structure.simple.HeightMode;
import com.ametrin.structures.structure.simple.PieceSources;
import com.ametrin.structures.structure.simple.SimpleStructure;
import com.ametrin.structures.structure.simple.TemplateEntry;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class StructureCheckTest {
    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath("test", "structure");

    /// Vanilla's own mistakes, and nothing else: a pool names a template that doesn't exist, a bastion
    /// path leads to a jigsaw name no template has, a chamber has a jigsaw into the wrong pool, and a
    /// zombie village's cats face the wrong way.
    @Test
    void findsOnlyVanillasKnownMistakes(MinecraftServer server) {
        var report = StructureCheck.run(server, structure -> structure.key().identifier().getNamespace().equals("minecraft"));
        assertEquals(List.of(
                "STRUCTURE minecraft:ancient_city missing_template",
                "STRUCTURE minecraft:bastion_remnant unmatched_jigsaw",
                "STRUCTURE minecraft:trial_chambers unmatched_jigsaw",
                "STRUCTURE minecraft:village_plains misfacing_jigsaw",
                "STRUCTURE minecraft:village_plains misfacing_jigsaw"), describe(report));
        assertTrue(report.structures() > 0);
        assertTrue(report.templates() > 0);
    }

    @Test
    void aMissingTemplateIsReported(MinecraftServer server) {
        var report = check(server, simple(Identifier.fromNamespaceAndPath("test", "missing")));
        assertEquals(List.of("STRUCTURE test:structure missing_template"), describe(report));
    }

    @Test
    void brokenReferencesInATemplateAreReported(MinecraftServer server) throws CommandSyntaxException {
        var template = template(server, "references", """
                {size: [2, 1, 1], entities: [],
                 palette: [{id: "minecraft:chest"}, {id: "ametrin_structures:fixture"}],
                 blocks: [
                   {pos: [0, 0, 0], state: 0, nbt: {id: "minecraft:chest", LootTable: "test:missing"}},
                   {pos: [1, 0, 0], state: 1, nbt: {id: "ametrin_structures:fixture", offset: [0.0d, 0.0d, 16.5d], fixtures: [
                     {type: "ametrin_structures:loot_container", loot_table: "test:missing"},
                     {type: "ametrin_structures:preset", preset: "test:missing"},
                     {type: "test:unknown"}]}}]}
                """);
        var report = check(server, simple(template));
        assertEquals(List.of(
                "TEMPLATE test:references fixture_offset",
                "TEMPLATE test:references missing_loot_table",
                "TEMPLATE test:references missing_reference",
                "TEMPLATE test:references missing_reference",
                "TEMPLATE test:references unreadable_fixture"), describe(report));
    }

    @Test
    void jigsawsThatCantConnectAreReported(MinecraftServer server) throws CommandSyntaxException {
        var template = template(server, "jigsaws", """
                {size: [3, 1, 1], entities: [],
                 palette: [{id: "minecraft:jigsaw", properties: {orientation: "west_up"}}],
                 blocks: [
                   {pos: [0, 0, 0], state: 0, nbt: {id: "minecraft:jigsaw", pool: "test:missing", name: "minecraft:empty", target: "test:a", joint: "rollable", final_state: "minecraft:air"}},
                   {pos: [1, 0, 0], state: 0, nbt: {id: "minecraft:jigsaw", pool: "minecraft:village/plains/terminators", name: "minecraft:empty", target: "test:nothing", joint: "rollable", final_state: "minecraft:air"}},
                   {pos: [2, 0, 0], state: 0, nbt: {id: "minecraft:jigsaw", pool: "minecraft:empty", name: "minecraft:empty", target: "test:a", joint: "rollable", final_state: "minecraft:air"}}]}
                """);
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var start = Holder.direct(new StructureTemplatePool(
                pools.getOrThrow(Pools.EMPTY),
                List.of(Pair.of(StructurePoolElement.single(template.toString()).apply(StructureTemplatePool.Projection.RIGID), 1))));
        var structure = new JigsawStructure(new Structure.StructureSettings(HolderSet.empty()), start, 2, ConstantHeight.of(VerticalAnchor.absolute(0)), false);
        // The jigsaw into the empty pool is meant to lead nowhere.
        var report = check(server, structure);
        assertEquals(List.of("STRUCTURE test:structure missing_pool", "STRUCTURE test:structure unmatched_jigsaw"), describe(report));
    }

    @Test
    void sidewaysJigsawsMeetAnySidewaysOneAndVerticalOnesOnlyTheirOpposite() {
        assertTrue(StructureCheck.canFace(jigsaw(FrontAndTop.WEST_UP), jigsaw(FrontAndTop.NORTH_UP)));
        assertFalse(StructureCheck.canFace(jigsaw(FrontAndTop.WEST_UP), jigsaw(FrontAndTop.DOWN_NORTH)));
        assertTrue(StructureCheck.canFace(jigsaw(FrontAndTop.UP_NORTH), jigsaw(FrontAndTop.DOWN_EAST)));
        assertFalse(StructureCheck.canFace(jigsaw(FrontAndTop.UP_NORTH), jigsaw(FrontAndTop.UP_NORTH)));
    }

    private static StructureTemplate.JigsawBlockInfo jigsaw(FrontAndTop orientation) {
        var state = Blocks.JIGSAW.defaultBlockState().setValue(JigsawBlock.ORIENTATION, orientation);
        return new StructureTemplate.JigsawBlockInfo(
                BlockPos.ZERO, state, JigsawBlockEntity.JointType.ROLLABLE, JigsawBlockEntity.EMPTY_ID, Pools.EMPTY, JigsawBlockEntity.EMPTY_ID, 0, 0);
    }

    private static StructureCheck.Report check(MinecraftServer server, Structure structure) {
        return new StructureCheck(server.getStructureTemplateManager(), server.registryAccess(), server.reloadableRegistries().lookup())
                .check(Map.of(STRUCTURE, structure));
    }

    private static SimpleStructure simple(Identifier template) {
        return new SimpleStructure(
                new Structure.StructureSettings(HolderSet.empty()),
                ExtendedStructureSettings.DEFAULT,
                new PieceSources.SingleSource(TemplateEntry.of(template)),
                Optional.empty(),
                SimpleStructure.ON_SURFACE_START_HEIGHT,
                HeightMode.CORNER,
                LiquidSettings.IGNORE_WATERLOGGING,
                Optional.<Foundation>empty(),
                Optional.empty());
    }

    /// Makes a template the server's template manager holds, from SNBT.
    private static Identifier template(MinecraftServer server, String path, String snbt) throws CommandSyntaxException {
        var id = Identifier.fromNamespaceAndPath("test", path);
        server.getStructureTemplateManager().getOrCreate(id).load(server.registryAccess().lookupOrThrow(Registries.BLOCK), TagParser.parseCompoundFully(snbt));
        return id;
    }

    /// Each problem as its subject, id and message key, sorted.
    private static List<String> describe(StructureCheck.Report report) {
        return report.problems().stream()
                .map(problem -> problem.subject() + " " + problem.id() + " "
                        + ((TranslatableContents) problem.message().getContents()).getKey().replace("commands.ametrin_structures.check.", ""))
                .sorted()
                .toList();
    }
}
