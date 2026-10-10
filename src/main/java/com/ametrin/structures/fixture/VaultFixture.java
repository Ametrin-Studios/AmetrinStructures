package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record VaultFixture(
        boolean ominous,
        Optional<ResourceKey<LootTable>> lootTable,
        Optional<ItemStackTemplate> keyItem,
        Optional<ResourceKey<LootTable>> displayLootTable) implements Fixture {
    public static final FixtureField<Boolean> OMINOUS = FixtureField.withDefault("ominous", FieldType.bool(), false);
    public static final FixtureField<Optional<ResourceKey<LootTable>>> LOOT_TABLE = FixtureField.optional("loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
    public static final FixtureField<Optional<ItemStackTemplate>> KEY_ITEM = FixtureField.optional("key_item", FieldType.item());
    public static final FixtureField<Optional<ResourceKey<LootTable>>> DISPLAY_LOOT_TABLE = FixtureField.optional("display_loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
    public static final List<FixtureField<?>> FIELDS = List.of(OMINOUS, LOOT_TABLE, KEY_ITEM, DISPLAY_LOOT_TABLE);
    public static final MapCodec<VaultFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    OMINOUS.forGetter(VaultFixture::ominous),
                    LOOT_TABLE.forGetter(VaultFixture::lootTable),
                    KEY_ITEM.forGetter(VaultFixture::keyItem),
                    DISPLAY_LOOT_TABLE.forGetter(VaultFixture::displayLootTable))
            .apply(instance, VaultFixture::new));

    public VaultFixture(boolean ominous) {
        this(ominous, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public VaultFixture(boolean ominous, ResourceKey<LootTable> lootTable) {
        this(ominous, Optional.of(lootTable), Optional.empty(), Optional.empty());
    }

    @Override
    public void apply(FixtureContext context) {
        context.placeBlock(Blocks.VAULT.defaultBlockState().setValue(VaultBlock.OMINOUS, ominous));
        if (context.level().getBlockEntity(context.actionBlockPos()) instanceof VaultBlockEntity vault) {
            // Marked as test-only, but it's the only way to set a vault's config other than loading it from a tag.
            vault.setConfig(config());
            vault.setChanged();
        }
    }

    VaultConfig config() {
        var defaultLootTable = ominous ? BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS : BuiltInLootTables.TRIAL_CHAMBERS_REWARD;
        var defaultKey = ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;
        return new VaultConfig(
                lootTable.orElse(defaultLootTable),
                VaultConfig.DEFAULT.activationRange(),
                VaultConfig.DEFAULT.deactivationRange(),
                keyItem.map(ItemStackTemplate::create).orElseGet(() -> new ItemStack(defaultKey)),
                displayLootTable);
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Fixtures.present(lootTable, displayLootTable);
    }

    @Override
    public FixtureType type() {
        return ASFixtures.VAULT.get();
    }
}
