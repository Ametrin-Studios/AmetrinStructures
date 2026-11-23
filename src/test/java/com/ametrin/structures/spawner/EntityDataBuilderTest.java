package com.ametrin.structures.spawner;

import com.ametrin.structures.util.ASNbt;
import com.ametrin.structures.util.ItemStackTemplate;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The builder writes entity data under the keys, and in the formats, the game reads.
@ExtendWith(EphemeralTestServerProvider.class)
class EntityDataBuilderTest {
    // Takes the server so the stacks read back have their components bound.
    @Test
    void theBuilderWritesWhatTheGameReads(MinecraftServer server) {
        var helmet = new ItemStackTemplate(Items.LEATHER_HELMET, DataComponentPatch.builder()
                .set(DataComponents.DYED_COLOR, new DyedItemColor(0xB02E26, true))
                .build());
        var tag = new EntityDataBuilder(EntityType.ZOMBIE)
                .name(Component.translatable("entity.example.gravedigger"))
                .nameVisible()
                .maxHealth(40)
                .attribute(Attributes.MOVEMENT_SPEED, 0.3)
                .effect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION))
                .leftHanded()
                .baby()
                .persistent()
                .tag("gravedigger")
                .equip(EquipmentSlot.MAINHAND, Items.IRON_SHOVEL)
                .equip(EquipmentSlot.HEAD, helmet)
                .dropChance(EquipmentSlot.MAINHAND, 1.0F)
                .passenger(new EntityDataBuilder(EntityType.CHICKEN).name(Component.literal("Clucky")))
                .build();

        assertEquals("minecraft:zombie", ASNbt.getStringOr(tag, "id", ""));
        assertEquals(Component.translatable("entity.example.gravedigger"), Component.Serializer.fromJson(ASNbt.getStringOr(tag, "CustomName", ""), server.registryAccess()));
        assertTrue(ASNbt.getBooleanOr(tag, "CustomNameVisible", false));
        assertEquals(40.0F, ASNbt.getFloatOr(tag, "Health", 0));
        var attributes = tag.getList("attributes", Tag.TAG_COMPOUND);
        assertEquals(40.0, base(attributes, "minecraft:generic.max_health"));
        assertEquals(0.3, base(attributes, "minecraft:generic.movement_speed"));
        assertTrue(ASNbt.read(tag, "active_effects", MobEffectInstance.CODEC.listOf()).orElseThrow().getFirst().is(MobEffects.MOVEMENT_SPEED));
        assertTrue(ASNbt.getBooleanOr(tag, "LeftHanded", false));
        assertTrue(ASNbt.getBooleanOr(tag, "IsBaby", false));
        assertEquals(AgeableMob.BABY_START_AGE, ASNbt.getIntOr(tag, "Age", 0));
        assertTrue(ASNbt.getBooleanOr(tag, "PersistenceRequired", false));
        assertEquals(List.of("gravedigger"), ASNbt.read(tag, "Tags", Codec.STRING.listOf()).orElseThrow());
        var registries = server.registryAccess();
        var hands = tag.getList("HandItems", Tag.TAG_COMPOUND);
        assertTrue(ItemStack.parseOptional(registries, hands.getCompound(0)).is(Items.IRON_SHOVEL));
        assertTrue(ItemStack.parseOptional(registries, hands.getCompound(1)).isEmpty());
        var head = ItemStack.parseOptional(registries, tag.getList("ArmorItems", Tag.TAG_COMPOUND).getCompound(3));
        assertEquals(new DyedItemColor(0xB02E26, true), head.get(DataComponents.DYED_COLOR));
        var handChances = tag.getList("HandDropChances", Tag.TAG_FLOAT);
        assertEquals(1.0F, handChances.getFloat(0));
        assertEquals(Mob.DEFAULT_EQUIPMENT_DROP_CHANCE, handChances.getFloat(1));
        assertFalse(tag.contains("ArmorDropChances"));
        var passenger = tag.getList("Passengers", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("minecraft:chicken", ASNbt.getStringOr(passenger, "id", ""));
        assertEquals("Clucky", Component.Serializer.fromJson(ASNbt.getStringOr(passenger, "CustomName", ""), server.registryAccess()).getString());
    }

    private static double base(ListTag attributes, String id) {
        return attributes.stream().map(CompoundTag.class::cast).filter(attribute -> attribute.getString("id").equals(id)).findFirst().orElseThrow().getDouble("base");
    }
}
