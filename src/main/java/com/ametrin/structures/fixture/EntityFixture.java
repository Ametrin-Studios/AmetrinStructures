package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.spawner.EntityDataBuilder;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.*;
import java.util.stream.Stream;

/// An entity facing the marker's front. Item frames and paintings hang on the block behind it.
/// Setting `nbt` turns off the entity's own randomization, like random armor, the same as for
/// spawners. Equipment and death loot don't.
///
/// @param items items per equipment slot, keyed by the slot's name, like `head`
public record EntityFixture(
        ResourceKey<EntityType<?>> entity,
        Optional<CompoundTag> nbt,
        Optional<ResourceKey<LootTable>> equipment,
        float equipmentDropChance,
        Optional<ResourceKey<LootTable>> deathLootTable,
        Map<EquipmentSlot, ItemStackTemplate> items) implements Fixture {
    public static final FixtureField<ResourceKey<EntityType<?>>> ENTITY = FixtureField.required("entity", FieldType.registryKey(Registries.ENTITY_TYPE));
    public static final FixtureField<Optional<CompoundTag>> NBT = FixtureField.optional("nbt", FieldType.nbt());
    public static final FixtureField<Optional<ResourceKey<LootTable>>> EQUIPMENT = FixtureField.optional("equipment", FieldType.registryKey(Registries.LOOT_TABLE));
    public static final FixtureField<Float> EQUIPMENT_DROP_CHANCE = FixtureField.withDefault("equipment_drop_chance", FieldType.number(0.0F, 1.0F), DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE);
    public static final FixtureField<Optional<ResourceKey<LootTable>>> DEATH_LOOT_TABLE = FixtureField.optional("death_loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
    public static final Map<EquipmentSlot, FixtureField<Optional<ItemStackTemplate>>> SLOTS = slotFields();
    public static final List<FixtureField<?>> FIELDS = Stream.concat(
            Stream.of(ENTITY, NBT, EQUIPMENT, EQUIPMENT_DROP_CHANCE, DEATH_LOOT_TABLE),
            SLOTS.values().stream()).toList();
    public static final MapCodec<EntityFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ENTITY.forGetter(EntityFixture::entity),
                    NBT.forGetter(EntityFixture::nbt),
                    EQUIPMENT.forGetter(EntityFixture::equipment),
                    EQUIPMENT_DROP_CHANCE.forGetter(EntityFixture::equipmentDropChance),
                    DEATH_LOOT_TABLE.forGetter(EntityFixture::deathLootTable),
                    itemsCodec().forGetter(EntityFixture::items))
            .apply(instance, EntityFixture::new));

    public EntityFixture {
        items = Map.copyOf(items);
    }

    public static EntityFixture of(EntityType<?> entity) {
        return of(new EntityDataBuilder(entity));
    }

    /// The death loot table goes into its own field, so it doesn't turn off the entity's randomization.
    public static EntityFixture of(EntityDataBuilder entity) {
        var extraData = entity.build();
        var deathLootTable = extraData.read(EntityDataBuilder.DEATH_LOOT_TABLE_KEY, LootTable.KEY_CODEC);
        extraData.remove("id");
        extraData.remove(EntityDataBuilder.DEATH_LOOT_TABLE_KEY);
        return new EntityFixture(
                BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity.type()).orElseThrow(),
                extraData.isEmpty() ? Optional.empty() : Optional.of(extraData),
                Optional.empty(),
                DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE,
                deathLootTable,
                Map.of());
    }

    public EntityFixture withEquipment(ResourceKey<LootTable> lootTable) {
        return withEquipment(lootTable, equipmentDropChance);
    }

    public EntityFixture withEquipment(ResourceKey<LootTable> lootTable, float dropChance) {
        return new EntityFixture(entity, nbt, Optional.of(lootTable), dropChance, deathLootTable, items);
    }

    @Override
    public void apply(FixtureContext context) {
        var type = Fixtures.entityType(entity);
        if (type.isEmpty()) {
            return;
        }
        Fixtures.spawnEntity(type.get(), nbt.orElse(null), context, spawned -> {
            if (spawned instanceof Mob mob) {
                // Set directly rather than through the extra data, so it keeps the entity's own randomization.
                deathLootTable.ifPresent(table -> mob.lootTable = Optional.of(table));
            }
            if (spawned instanceof LivingEntity living) {
                equipment.ifPresent(table -> equip(living, new EquipmentTable(table, equipmentDropChance)));
                items.forEach((slot, item) -> living.setItemSlot(slot, item.create()));
            }
        });
    }

    // Only mobs roll equipment tables themselves. Other living entities, like armor stands, use the same
    // slot rules but get no drop chances, since they drop everything they hold anyway.
    private static void equip(LivingEntity living, EquipmentTable table) {
        if (living instanceof Mob mob) {
            mob.equip(table);
            return;
        }
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, living.position())
                .withParameter(LootContextParams.THIS_ENTITY, living)
                .create(LootContextParamSets.EQUIPMENT);
        var user = new EquipmentUser() {
            @Override
            public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
                living.setItemSlot(slot, stack);
            }

            @Override
            public ItemStack getItemBySlot(EquipmentSlot slot) {
                return living.getItemBySlot(slot);
            }

            @Override
            public void setDropChance(EquipmentSlot slot, float dropChance) {}
        };
        user.equip(table, params);
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Stream.concat(Stream.of(entity), Fixtures.present(equipment, deathLootTable));
    }

    @Override
    public FixtureType type() {
        return ASFixtures.ENTITY.get();
    }

    private static Map<EquipmentSlot, FixtureField<Optional<ItemStackTemplate>>> slotFields() {
        var fields = new EnumMap<EquipmentSlot, FixtureField<Optional<ItemStackTemplate>>>(EquipmentSlot.class);
        for (var slot : EquipmentSlot.values()) {
            fields.put(slot, FixtureField.optional(slot.getName(), FieldType.item()));
        }
        return fields;
    }

    // One optional field per slot, gathered into a map.
    private static MapCodec<Map<EquipmentSlot, ItemStackTemplate>> itemsCodec() {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Arrays.stream(EquipmentSlot.values()).map(slot -> ops.createString(slot.getName()));
            }

            @Override
            public <T> DataResult<Map<EquipmentSlot, ItemStackTemplate>> decode(DynamicOps<T> ops, MapLike<T> input) {
                DataResult<Map<EquipmentSlot, ItemStackTemplate>> result = DataResult.success(new EnumMap<>(EquipmentSlot.class));
                for (var slot : EquipmentSlot.values()) {
                    var value = input.get(slot.getName());
                    if (value != null) {
                        result = result.apply2((items, item) -> {
                            items.put(slot, item);
                            return items;
                        }, ItemStackTemplate.CODEC.parse(ops, value));
                    }
                }
                return result;
            }

            @Override
            public <T> RecordBuilder<T> encode(Map<EquipmentSlot, ItemStackTemplate> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                input.forEach((slot, item) -> prefix.add(slot.getName(), ItemStackTemplate.CODEC.encodeStart(ops, item)));
                return prefix;
            }
        };
    }
}
