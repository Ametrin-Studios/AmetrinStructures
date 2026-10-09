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
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Setting anything but [#equipment] and [#spawnRules] makes the entity skip its own spawn randomization, such as random armor and handedness, as vanilla spawners do.
/// An entity fixture also keeps it with a [#deathLootTable].
public final class SpawnDataBuilder {
    public static final String DEATH_LOOT_TABLE_KEY = "DeathLootTable";

    private final EntityType<?> entity;
    private final CompoundTag data = new CompoundTag();
    private final CompoundTag equipment = new CompoundTag();
    private final CompoundTag dropChances = new CompoundTag();
    private final Map<Holder<Attribute>, Double> attributes = new LinkedHashMap<>();
    private final List<MobEffectInstance> effects = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private final ListTag passengers = new ListTag();
    private @Nullable EquipmentTable equipmentTable;
    private SpawnData.@Nullable CustomSpawnRules spawnRules;

    public SpawnDataBuilder(EntityType<?> entity) {
        this.entity = entity;
    }

    public SpawnDataBuilder name(Component name) {
        data.store("CustomName", ComponentSerialization.CODEC, name);
        return this;
    }

    /// Shows the name even when the entity isn't looked at.
    public SpawnDataBuilder nameVisible() {
        data.putBoolean("CustomNameVisible", true);
        return this;
    }

    public SpawnDataBuilder health(float health) {
        data.putFloat("Health", health);
        return this;
    }

    public SpawnDataBuilder maxHealth(double maxHealth) {
        return attribute(Attributes.MAX_HEALTH, maxHealth).health((float) maxHealth);
    }

    public SpawnDataBuilder attribute(Holder<Attribute> attribute, double base) {
        attributes.put(attribute, base);
        return this;
    }

    public SpawnDataBuilder effect(MobEffectInstance effect) {
        effects.add(effect);
        return this;
    }

    public SpawnDataBuilder leftHanded() {
        data.putBoolean("LeftHanded", true);
        return this;
    }

    public SpawnDataBuilder baby() {
        data.putInt("Age", AgeableMob.BABY_START_AGE);
        data.putBoolean("IsBaby", true);
        return this;
    }

    public SpawnDataBuilder persistent() {
        data.putBoolean("PersistenceRequired", true);
        return this;
    }

    public SpawnDataBuilder canPickUpLoot() {
        data.putBoolean("CanPickUpLoot", true);
        return this;
    }

    public SpawnDataBuilder noAi() {
        data.putBoolean("NoAI", true);
        return this;
    }

    public SpawnDataBuilder silent() {
        data.putBoolean("Silent", true);
        return this;
    }

    public SpawnDataBuilder glowing() {
        data.putBoolean("Glowing", true);
        return this;
    }

    public SpawnDataBuilder invulnerable() {
        data.putBoolean("Invulnerable", true);
        return this;
    }

    /// A scoreboard tag, for commands.
    public SpawnDataBuilder tag(String tag) {
        tags.add(tag);
        return this;
    }

    public SpawnDataBuilder equipment(ResourceKey<LootTable> lootTable) {
        return equipment(new EquipmentTable(lootTable, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE));
    }

    public SpawnDataBuilder equipment(EquipmentTable equipmentTable) {
        this.equipmentTable = equipmentTable;
        return this;
    }

    public SpawnDataBuilder equip(EquipmentSlot slot, ItemLike item) {
        return equip(slot, new ItemStackTemplate(item.asItem()));
    }

    /// Stacks referring to datapack content, such as enchanted ones, need [#equip(EquipmentSlot, ItemStackTemplate, HolderLookup.Provider)], which datagen can't call: use an equipment loot table there.
    public SpawnDataBuilder equip(EquipmentSlot slot, ItemStackTemplate stack) {
        return equip(slot, stack, NbtOps.INSTANCE);
    }

    public SpawnDataBuilder equip(EquipmentSlot slot, ItemStackTemplate stack, HolderLookup.Provider registries) {
        return equip(slot, stack, registries.createSerializationContext(NbtOps.INSTANCE));
    }

    // An item stack reads the same fields.
    private SpawnDataBuilder equip(EquipmentSlot slot, ItemStackTemplate stack, DynamicOps<Tag> ops) {
        equipment.put(slot.getSerializedName(), ItemStackTemplate.MAP_CODEC.codec().encodeStart(ops, stack).getOrThrow());
        return this;
    }

    public SpawnDataBuilder deathLootTable(ResourceKey<LootTable> lootTable) {
        data.store(DEATH_LOOT_TABLE_KEY, LootTable.KEY_CODEC, lootTable);
        return this;
    }

    /// Default is 0.085. 1 or more always drops undamaged.
    public SpawnDataBuilder dropChance(EquipmentSlot slot, float chance) {
        dropChances.putFloat(slot.getSerializedName(), chance);
        return this;
    }

    /// Replaces the entity's own spawn rules with light ranges from 0 to 15.
    /// Spawners only.
    public SpawnDataBuilder spawnRules(InclusiveRange<Integer> blockLightLimit, InclusiveRange<Integer> skyLightLimit) {
        return spawnRules(new SpawnData.CustomSpawnRules(blockLightLimit, skyLightLimit));
    }

    /// Replaces the entity's own spawn rules with light ranges from 0 to 15.
    /// Spawners only.
    public SpawnDataBuilder spawnRules(SpawnData.CustomSpawnRules spawnRules) {
        this.spawnRules = spawnRules;
        return this;
    }

    /// Spawners only.
    public SpawnDataBuilder noLightLimit() {
        var any = new InclusiveRange<>(0, 15);
        return spawnRules(any, any);
    }

    public SpawnDataBuilder passenger(EntityType<?> passenger) {
        return passenger(new SpawnDataBuilder(passenger));
    }

    /// equipment tables don't apply
    public SpawnDataBuilder passenger(SpawnDataBuilder passenger) {
        return passenger(passenger.entityTag());
    }

    public SpawnDataBuilder passenger(CompoundTag passenger) {
        passengers.add(passenger);
        return this;
    }

    public SpawnData build() {
        return new SpawnData(entityTag(), Optional.ofNullable(spawnRules), Optional.ofNullable(equipmentTable));
    }

    public Weighted<SpawnData> build(int weight) {
        return new Weighted<>(build(), weight);
    }

    private CompoundTag entityTag() {
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
