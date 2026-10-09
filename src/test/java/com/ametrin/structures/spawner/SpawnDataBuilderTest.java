package com.ametrin.structures.spawner;

import net.minecraft.util.InclusiveRange;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentTable;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnDataBuilderTest {
    // They sit next to the entity's data, so it keeps its own spawn randomization.
    @Test
    void equipmentAndSpawnRulesLeaveTheEntityAlone() {
        var data = SpawnDataBuilder.of(EntityType.ZOMBIE)
                .equipment(BuiltInLootTables.SIMPLE_DUNGEON)
                .noLightLimit()
                .build();

        var any = new InclusiveRange<>(0, 15);
        assertEquals(Optional.of(new SpawnData.CustomSpawnRules(any, any)), data.customSpawnRules());
        assertEquals(Optional.of(BuiltInLootTables.SIMPLE_DUNGEON), data.equipment().map(EquipmentTable::lootTable));
        assertEquals(1, data.entityToSpawn().size());
    }
}
