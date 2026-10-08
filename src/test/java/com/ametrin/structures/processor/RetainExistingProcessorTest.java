package com.ametrin.structures.processor;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class RetainExistingProcessorTest {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState GRASS = Blocks.SHORT_GRASS.defaultBlockState();

    @Test
    void theFlagSelectsTheVariant() {
        assertSame(RetainExistingProcessor.ALL, parse("{}"));
        assertSame(RetainExistingProcessor.REPLACEABLE_ONLY, parse("{\"replaceable_only\":true}"));
    }

    @Test
    void allHoldsBackEveryBlockWhereTheWorldIsSolid() {
        assertNull(process(RetainExistingProcessor.ALL, STONE, STONE));
        assertNull(process(RetainExistingProcessor.ALL, AIR, STONE));
        assertEquals(STONE, process(RetainExistingProcessor.ALL, STONE, GRASS));
    }

    @Test
    void replaceableOnlyAlwaysPlacesSolidBlocks() {
        assertEquals(STONE, process(RetainExistingProcessor.REPLACEABLE_ONLY, STONE, STONE));
    }

    @Test
    void replaceableOnlyKeepsTemplateAirOutOfSolidTerrain() {
        assertNull(process(RetainExistingProcessor.REPLACEABLE_ONLY, AIR, STONE));
        assertEquals(AIR, process(RetainExistingProcessor.REPLACEABLE_ONLY, AIR, GRASS));
    }

    private static RetainExistingProcessor parse(String json) {
        return RetainExistingProcessor.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static @Nullable BlockState process(RetainExistingProcessor processor, BlockState template, BlockState world) {
        var info = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, template, (CompoundTag) null);
        var result = processor.process(level(world), BlockPos.ZERO, BlockPos.ZERO, info, info, new StructurePlaceSettings(), null);
        return result == null ? null : result.state();
    }

    // The processor only reads the block at the position.
    private static LevelReader level(BlockState world) {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class}, (_, method, _) -> {
            if (method.getName().equals("getBlockState")) {
                return world;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }
}
