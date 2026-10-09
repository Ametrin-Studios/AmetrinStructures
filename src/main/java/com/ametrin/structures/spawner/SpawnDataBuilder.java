package com.ametrin.structures.spawner;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentTable;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class SpawnDataBuilder {
    private final EntityDataBuilder entity;
    private @Nullable EquipmentTable equipment;
    private SpawnData.@Nullable CustomSpawnRules spawnRules;

    private SpawnDataBuilder(EntityDataBuilder entity) {
        this.entity = entity;
    }

    public static SpawnDataBuilder of(EntityType<?> entity) {
        return of(new EntityDataBuilder(entity));
    }

    public static SpawnDataBuilder of(EntityDataBuilder entity) {
        return new SpawnDataBuilder(entity);
    }

    public SpawnDataBuilder equipment(ResourceKey<LootTable> lootTable) {
        return equipment(new EquipmentTable(lootTable, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE));
    }

    public SpawnDataBuilder equipment(EquipmentTable equipment) {
        this.equipment = equipment;
        return this;
    }

    /// Replaces the entity's own spawn rules with light ranges from 0 to 15
    public SpawnDataBuilder spawnRules(InclusiveRange<Integer> blockLightLimit, InclusiveRange<Integer> skyLightLimit) {
        return spawnRules(new SpawnData.CustomSpawnRules(blockLightLimit, skyLightLimit));
    }

    /// Replaces the entity's own spawn rules
    public SpawnDataBuilder spawnRules(SpawnData.CustomSpawnRules spawnRules) {
        this.spawnRules = spawnRules;
        return this;
    }

    public SpawnDataBuilder noLightLimit() {
        var any = new InclusiveRange<>(0, 15);
        return spawnRules(any, any);
    }

    public SpawnData build() {
        return new SpawnData(entity.build(), Optional.ofNullable(spawnRules), Optional.ofNullable(equipment));
    }

    public Weighted<SpawnData> build(int weight) {
        return new Weighted<>(build(), weight);
    }
}
