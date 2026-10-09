package com.ametrin.structures.spawner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// An entity's data, for spawners, entity fixtures and passengers.
///
/// Setting anything makes a spawner's entity skip its own spawn randomization, such as random armor and handedness, as vanilla spawners do.
/// An entity fixture also keeps it with a [#deathLootTable].
public final class EntityDataBuilder {
    public static final String DEATH_LOOT_TABLE_KEY = "DeathLootTable";

    private final EntityType<?> entity;
    private final CompoundTag data = new CompoundTag();
    private final CompoundTag equipment = new CompoundTag();
    private final CompoundTag dropChances = new CompoundTag();
    private final Map<Holder<Attribute>, Double> attributes = new LinkedHashMap<>();
    private final List<MobEffectInstance> effects = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private final ListTag passengers = new ListTag();

    public EntityDataBuilder(EntityType<?> entity) {
        this.entity = entity;
    }

    public static EntityDataBuilder of(EntityType<?> entity) {
        return new EntityDataBuilder(entity);
    }

    public EntityDataBuilder name(Component name) {
        data.store("CustomName", ComponentSerialization.CODEC, name);
        return this;
    }

    /// Shows the name even when the entity isn't looked at.
    public EntityDataBuilder nameVisible() {
        data.putBoolean("CustomNameVisible", true);
        return this;
    }

    public EntityDataBuilder health(float health) {
        data.putFloat("Health", health);
        return this;
    }

    public EntityDataBuilder maxHealth(double maxHealth) {
        return attribute(Attributes.MAX_HEALTH, maxHealth).health((float) maxHealth);
    }

    public EntityDataBuilder attribute(Holder<Attribute> attribute, double base) {
        attributes.put(attribute, base);
        return this;
    }

    public EntityDataBuilder effect(MobEffectInstance effect) {
        effects.add(effect);
        return this;
    }

    public EntityDataBuilder leftHanded() {
        data.putBoolean("LeftHanded", true);
        return this;
    }

    public EntityDataBuilder baby() {
        data.putInt("Age", AgeableMob.BABY_START_AGE);
        data.putBoolean("IsBaby", true);
        return this;
    }

    public EntityDataBuilder persistent() {
        data.putBoolean("PersistenceRequired", true);
        return this;
    }

    public EntityDataBuilder canPickUpLoot() {
        data.putBoolean("CanPickUpLoot", true);
        return this;
    }

    public EntityDataBuilder noAi() {
        data.putBoolean("NoAI", true);
        return this;
    }

    public EntityDataBuilder silent() {
        data.putBoolean("Silent", true);
        return this;
    }

    public EntityDataBuilder glowing() {
        data.putBoolean("Glowing", true);
        return this;
    }

    public EntityDataBuilder invulnerable() {
        data.putBoolean("Invulnerable", true);
        return this;
    }

    /// A scoreboard tag, for commands.
    public EntityDataBuilder tag(String tag) {
        tags.add(tag);
        return this;
    }

    public EntityDataBuilder equip(EquipmentSlot slot, ItemLike item) {
        return equip(slot, new ItemStackTemplate(item.asItem()));
    }

    /// Stacks referring to datapack content, such as enchanted ones, need [#equip(EquipmentSlot, ItemStackTemplate, HolderLookup.Provider)], which datagen can't call: use an equipment loot table there.
    public EntityDataBuilder equip(EquipmentSlot slot, ItemStackTemplate stack) {
        return equip(slot, stack, NbtOps.INSTANCE);
    }

    public EntityDataBuilder equip(EquipmentSlot slot, ItemStackTemplate stack, HolderLookup.Provider registries) {
        return equip(slot, stack, registries.createSerializationContext(NbtOps.INSTANCE));
    }

    // An item stack reads the same fields.
    private EntityDataBuilder equip(EquipmentSlot slot, ItemStackTemplate stack, DynamicOps<Tag> ops) {
        equipment.put(slot.getSerializedName(), ItemStackTemplate.MAP_CODEC.codec().encodeStart(ops, stack).getOrThrow());
        return this;
    }

    public EntityDataBuilder deathLootTable(ResourceKey<LootTable> lootTable) {
        data.store(DEATH_LOOT_TABLE_KEY, LootTable.KEY_CODEC, lootTable);
        return this;
    }

    /// Default is 0.085. 1 or more always drops undamaged.
    public EntityDataBuilder dropChance(EquipmentSlot slot, float chance) {
        dropChances.putFloat(slot.getSerializedName(), chance);
        return this;
    }

    public EntityDataBuilder passenger(EntityType<?> passenger) {
        return passenger(new EntityDataBuilder(passenger));
    }

    public EntityDataBuilder passenger(EntityDataBuilder passenger) {
        return passenger(passenger.build());
    }

    public EntityDataBuilder passenger(CompoundTag passenger) {
        passengers.add(passenger);
        return this;
    }

    public EntityType<?> type() {
        return entity;
    }

    public CompoundTag build() {
        var tag = data.copy();
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity).toString());
        if (!attributes.isEmpty()) {
            tag.store("attributes", AttributeInstance.Packed.LIST_CODEC, attributes.entrySet().stream()
                    .map(attribute -> new AttributeInstance.Packed(attribute.getKey(), attribute.getValue(), List.of()))
                    .toList());
        }
        if (!effects.isEmpty()) {
            tag.store("active_effects", MobEffectInstance.CODEC.listOf(), effects);
        }
        if (!tags.isEmpty()) {
            tag.store("Tags", Codec.STRING.listOf(), tags);
        }
        if (!equipment.isEmpty()) {
            tag.put("equipment", equipment.copy());
        }
        if (!dropChances.isEmpty()) {
            tag.put("drop_chances", dropChances.copy());
        }
        if (!passengers.isEmpty()) {
            tag.put("Passengers", passengers.copy());
        }
        return tag;
    }
}
