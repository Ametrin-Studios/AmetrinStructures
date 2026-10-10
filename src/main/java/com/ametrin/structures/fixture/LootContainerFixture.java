package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.stream.Stream;

/// Any block whose block entity takes a loot table
public record LootContainerFixture(ResourceKey<LootTable> lootTable, BlockState block) implements Fixture {
    public static final FixtureField<ResourceKey<LootTable>> LOOT_TABLE = FixtureField.required("loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
    public static final FixtureField<BlockState> BLOCK = FixtureField.withDefault("block", BlockStateMerging.mergedBlockState().validated(Fixtures::isLootContainer, "the block must take a loot table"), Blocks.CHEST.defaultBlockState());
    public static final List<FixtureField<?>> FIELDS = List.of(LOOT_TABLE, BLOCK);
    public static final MapCodec<LootContainerFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    LOOT_TABLE.forGetter(LootContainerFixture::lootTable),
                    BLOCK.forGetter(LootContainerFixture::block))
            .apply(instance, LootContainerFixture::new));

    public static LootContainerFixture chest(ResourceKey<LootTable> lootTable) {
        return new LootContainerFixture(lootTable, Blocks.CHEST.defaultBlockState());
    }

    @Override
    public void apply(FixtureContext context) {
        context.placeBlock(block);
        RandomizableContainer.setBlockEntityLootTable(context.level(), context.random(), context.actionBlockPos(), lootTable);
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Stream.of(lootTable);
    }

    @Override
    public FixtureType type() {
        return ASFixtures.LOOT_CONTAINER.get();
    }
}
