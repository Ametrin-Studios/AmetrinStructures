package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// Suspicious sand or gravel, holding an item from `loot_table` when set.
public record ArchaeologyFixture(Optional<ResourceKey<LootTable>> lootTable, BlockState block) implements Fixture {
    public static final FixtureField<Optional<ResourceKey<LootTable>>> LOOT_TABLE = FixtureField.optional("loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
    public static final FixtureField<BlockState> BLOCK = FixtureField.withDefault("block", FieldType.blockState(), Blocks.SUSPICIOUS_SAND.defaultBlockState());
    public static final List<FixtureField<?>> FIELDS = List.of(LOOT_TABLE, BLOCK);
    public static final MapCodec<ArchaeologyFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    LOOT_TABLE.forGetter(ArchaeologyFixture::lootTable),
                    BLOCK.forGetter(ArchaeologyFixture::block))
            .apply(instance, ArchaeologyFixture::new));

    @Override
    public void apply(FixtureContext context) {
        context.placeBlock(block);
        if (context.level().getBlockEntity(context.actionBlockPos()) instanceof BrushableBlockEntity brushable) {
            lootTable.ifPresent(loot -> brushable.setLootTable(loot, context.random().nextLong()));
        }
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Fixtures.present(lootTable);
    }

    @Override
    public FixtureType type() {
        return ASFixtures.ARCHAEOLOGY.get();
    }
}
