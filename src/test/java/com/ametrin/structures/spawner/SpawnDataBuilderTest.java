package com.ametrin.structures.spawner;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.SpawnData;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The builder writes entity data under the keys, and in the formats, the game reads.
@ExtendWith(EphemeralTestServerProvider.class)
class SpawnDataBuilderTest {
    // Takes the server so the stacks read back have their components bound.
    @Test
    void theBuilderWritesWhatTheGameReads(MinecraftServer server) {
        var helmet = new ItemStackTemplate(Items.LEATHER_HELMET, DataComponentPatch.builder()
                .set(DataComponents.DYED_COLOR, new DyedItemColor(0xB02E26))
                .build());
        var tag = new SpawnDataBuilder(EntityType.ZOMBIE)
                .name(Component.literal("Gravedigger"))
                .nameVisible()
                .maxHealth(40)
                .attribute(Attributes.MOVEMENT_SPEED, 0.3)
                .effect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION))
                .leftHanded()
                .baby()
                .persistent()
                .tag("gravedigger")
                .equip(EquipmentSlot.MAINHAND, Items.IRON_SHOVEL)
                .equip(EquipmentSlot.HEAD, helmet)
                .dropChance(EquipmentSlot.MAINHAND, 1.0F)
                .passenger(new SpawnDataBuilder(EntityType.CHICKEN).name(Component.literal("Clucky")))
                .build()
                .entityToSpawn();

        assertEquals("minecraft:zombie", tag.getStringOr("id", ""));
        assertEquals("Gravedigger", tag.read("CustomName", ComponentSerialization.CODEC).orElseThrow().getString());
        assertTrue(tag.getBooleanOr("CustomNameVisible", false));
        assertEquals(40.0F, tag.getFloatOr("Health", 0));
        var attributes = tag.read("attributes", AttributeInstance.Packed.LIST_CODEC).orElseThrow();
        assertEquals(40.0, attributes.stream().filter(a -> a.attribute().equals(Attributes.MAX_HEALTH)).findFirst().orElseThrow().baseValue());
        assertEquals(0.3, attributes.stream().filter(a -> a.attribute().equals(Attributes.MOVEMENT_SPEED)).findFirst().orElseThrow().baseValue());
        assertTrue(tag.read("active_effects", MobEffectInstance.CODEC.listOf()).orElseThrow().getFirst().is(MobEffects.SPEED));
        assertTrue(tag.getBooleanOr("LeftHanded", false));
        assertTrue(tag.getBooleanOr("IsBaby", false));
        assertEquals(AgeableMob.BABY_START_AGE, tag.getIntOr("Age", 0));
        assertTrue(tag.getBooleanOr("PersistenceRequired", false));
        assertEquals(List.of("gravedigger"), tag.read("Tags", Codec.STRING.listOf()).orElseThrow());
        var equipment = tag.read("equipment", EntityEquipment.CODEC).orElseThrow();
        assertTrue(equipment.get(EquipmentSlot.MAINHAND).is(Items.IRON_SHOVEL));
        assertEquals(new DyedItemColor(0xB02E26), equipment.get(EquipmentSlot.HEAD).get(DataComponents.DYED_COLOR));
        assertEquals(1.0F, tag.read("drop_chances", DropChances.CODEC).orElseThrow().byEquipment(EquipmentSlot.MAINHAND));
        var passenger = tag.getListOrEmpty("Passengers").getCompoundOrEmpty(0);
        assertEquals("minecraft:chicken", passenger.getStringOr("id", ""));
        assertEquals("Clucky", passenger.read("CustomName", ComponentSerialization.CODEC).orElseThrow().getString());
    }

    // The rules sit next to the entity, so it keeps its own spawn randomization.
    @Test
    void spawnRulesLeaveTheEntityAlone() {
        var data = new SpawnDataBuilder(EntityType.ZOMBIE).noLightLimit().build();

        var any = new InclusiveRange<>(0, 15);
        assertEquals(Optional.of(new SpawnData.CustomSpawnRules(any, any)), data.customSpawnRules());
        assertEquals(1, data.entityToSpawn().size());
    }
}
