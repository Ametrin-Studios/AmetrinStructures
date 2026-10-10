package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASAttachments;
import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.spawner.*;
import com.ametrin.structures.structure.Foundation;
import com.ametrin.structures.util.ASLog;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner.FullConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Fixtures {
    private static final int SPAWN_DELAY_LIMIT = 72_000;

    private Fixtures() {}

    /// Places the becomes state and nothing else.
    public record Empty() implements Fixture {
        public static final Empty INSTANCE = new Empty();
        public static final MapCodec<Empty> CODEC = MapCodec.unit(INSTANCE);
        public static final List<FixtureField<?>> FIELDS = List.of();

        @Override
        public void apply(FixtureContext context) {
        }

        @Override
        public FixtureType type() {
            return ASFixtures.EMPTY.get();
        }
    }

    /// Draws from a [FixturePreset] and runs what it draws, as if the fixture was that itself.
    public record Preset(ResourceKey<FixturePreset> preset) implements Fixture {
        public static final FixtureField<ResourceKey<FixturePreset>> PRESET = FixtureField.required("preset", FieldType.registryKey(ASRegistries.FIXTURE_PRESET));
        public static final List<FixtureField<?>> FIELDS = List.of(PRESET);
        public static final MapCodec<Preset> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(PRESET.forGetter(Preset::preset)).apply(instance, Preset::new));

        @Override
        public void apply(FixtureContext context) {
            // should never run
        }

        @Override
        public Stream<ResourceKey<?>> references() {
            return Stream.of(preset);
        }

        @Override
        public FixtureType type() {
            return ASFixtures.PRESET.get();
        }
    }

    /// Any block whose block entity takes a loot table
    public record LootContainer(ResourceKey<LootTable> lootTable, BlockState block) implements Fixture {
        public static final FixtureField<ResourceKey<LootTable>> LOOT_TABLE = FixtureField.required("loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
        public static final FixtureField<BlockState> BLOCK = FixtureField.withDefault("block", BlockStateMerging.mergedBlockState().validated(Fixtures::isLootContainer, "the block must take a loot table"), Blocks.CHEST.defaultBlockState());
        public static final List<FixtureField<?>> FIELDS = List.of(LOOT_TABLE, BLOCK);
        public static final MapCodec<LootContainer> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        LOOT_TABLE.forGetter(LootContainer::lootTable),
                        BLOCK.forGetter(LootContainer::block))
                .apply(instance, LootContainer::new));

        public static LootContainer chest(ResourceKey<LootTable> lootTable) {
            return new LootContainer(lootTable, Blocks.CHEST.defaultBlockState());
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

    /// Any block with an inventory, such as a brewing stand or a furnace, filled from `loot_table` as the
    /// structure generates. Unlike a [LootContainer] the loot is drawn right away, not when a player
    /// first opens it, so it works for blocks that don't take a loot table. Each item goes into an
    /// empty slot that accepts it, the one that accepts the fewest kinds of items, so blaze powder goes
    /// to a brewing stand's fuel slot; among equal slots the choice is random. What fits nowhere is
    /// left out. The loot table runs during
    /// world generation, so keep it free of functions that search the world, such as exploration maps.
    ///
    /// A brewing stand's bottles follow the loot: the stand sets them itself once it ticks, so `block`
    /// leaves them out.
    public record FillContainer(ResourceKey<LootTable> lootTable, BlockState block) implements Fixture {
        private static final Map<BlockEntityType<?>, int[]> SLOT_BREADTH = new ConcurrentHashMap<>();

        public static final FixtureField<ResourceKey<LootTable>> LOOT_TABLE = FixtureField.required("loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
        public static final FixtureField<BlockState> BLOCK = FixtureField.withDefault(
                "block",
                BlockStateMerging.mergedBlockState(List.of(BrewingStandBlock.HAS_BOTTLE))
                        .validated(Fixtures::isContainer, "the block must have an inventory"),
                Blocks.BREWING_STAND.defaultBlockState());
        public static final List<FixtureField<?>> FIELDS = List.of(LOOT_TABLE, BLOCK);
        public static final MapCodec<FillContainer> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        LOOT_TABLE.forGetter(FillContainer::lootTable),
                        BLOCK.forGetter(FillContainer::block))
                .apply(instance, FillContainer::new));

        @Override
        public void apply(FixtureContext context) {
            var pos = context.actionBlockPos();
            context.placeBlock(block);
            if (!(context.level().getBlockEntity(pos) instanceof BlockEntity blockEntity && blockEntity instanceof Container container)) {
                return;
            }
            var level = context.level().getLevel();
            var params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .create(LootContextParamSets.CHEST);
            var items = level.getServer().reloadableRegistries().getLootTable(lootTable).getRandomItems(params, context.random());
            var leftOver = fill(container, judge(blockEntity, level), items, context.random());
            if (!leftOver.isEmpty()) {
                ASLog.debug("fixture container {} at {} had no room for {}", block, pos, leftOver);
            }
        }

        // A block entity that is still generating has no level, yet some ask theirs whether an item
        // fits: a brewing stand turns potions away without one. A copy that knows the level decides
        // instead; it is never placed, so setting its level has no effect on the world.
        private static Container judge(BlockEntity blockEntity, Level level) {
            if (blockEntity.hasLevel() || !(blockEntity.getType().create(blockEntity.getBlockPos(), blockEntity.getBlockState()) instanceof Container copy)) {
                return (Container) blockEntity;
            }
            ((BlockEntity) copy).setLevel(level);
            return copy;
        }

        /// Puts each stack into empty slots of `container`, splitting it to fit. `judge`, a container
        /// of the same kind, decides which slots take it. The [narrowest][#breadth(Container)] slot
        /// that takes it wins, so blaze powder fuels a brewing stand instead of brewing in it; among
        /// equally narrow slots the choice is random.
        ///
        /// @return what didn't fit
        // Items are set as inside a transaction, which skips side effects such as a chiseled bookshelf
        // updating its block in a level that a generating block entity doesn't have.
        static List<ItemStack> fill(Container container, Container judge, List<ItemStack> items, RandomSource random) {
            var slots = IntStream.range(0, container.getContainerSize()).boxed().collect(Collectors.toCollection(ArrayList::new));
            Util.shuffle(slots, random);
            var breadth = breadth(judge);
            // A stable sort, so equally narrow slots keep their random order.
            slots.sort(Comparator.comparingInt(slot -> slot < breadth.length ? breadth[slot] : Integer.MAX_VALUE));
            var leftOver = new ArrayList<ItemStack>();
            for (var stack : items) {
                for (var iterator = slots.iterator(); iterator.hasNext() && !stack.isEmpty(); ) {
                    int slot = iterator.next();
                    var portion = stack.copyWithCount(Math.min(stack.getCount(), container.getMaxStackSize(stack)));
                    if (container.getItem(slot).isEmpty() && judge.canPlaceItem(slot, portion)) {
                        container.setItem(slot, portion, true);
                        // Some turn items away in setItem itself: a chiseled bookshelf anything but books.
                        if (!container.getItem(slot).isEmpty()) {
                            stack.shrink(portion.getCount());
                            iterator.remove();
                        }
                    }
                }
                if (!stack.isEmpty()) {
                    leftOver.add(stack);
                }
            }
            return leftOver;
        }

        /// How many kinds of items each slot of `judge` takes, counted over every registered item.
        static int[] breadth(Container judge) {
            // Kept per block entity type: it only orders slots, so a datapack changing what a slot
            // takes, such as brewing ingredients, at worst leaves the order a little off.
            if (judge instanceof BlockEntity blockEntity) {
                var known = SLOT_BREADTH.computeIfAbsent(blockEntity.getType(), _ -> countBreadth(judge));
                if (known.length == judge.getContainerSize()) {
                    return known;
                }
            }
            return countBreadth(judge);
        }

        private static int[] countBreadth(Container judge) {
            var breadth = new int[judge.getContainerSize()];
            for (var item : BuiltInRegistries.ITEM) {
                var probe = item.getDefaultInstance();
                if (probe.isEmpty()) {
                    continue;
                }
                for (int slot = 0; slot < breadth.length; slot++) {
                    if (judge.canPlaceItem(slot, probe)) {
                        breadth[slot]++;
                    }
                }
            }
            return breadth;
        }

        @Override
        public Stream<ResourceKey<?>> references() {
            return Stream.of(lootTable);
        }

        @Override
        public FixtureType type() {
            return ASFixtures.FILL_CONTAINER.get();
        }
    }

    /// Any block state, with the marker's orientation merged in.
    public record PlaceBlockState(BlockState state) implements Fixture {
        public static final FixtureField<BlockState> STATE = FixtureField.required("state", BlockStateMerging.mergedBlockState());
        public static final List<FixtureField<?>> FIELDS = List.of(STATE);
        public static final MapCodec<PlaceBlockState> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        STATE.forGetter(PlaceBlockState::state))
                .apply(instance, PlaceBlockState::new));

        @Override
        public void apply(FixtureContext context) {
            context.placeBlock(state);
        }

        @Override
        public FixtureType type() {
            return ASFixtures.BLOCK_STATE.get();
        }
    }

    /// An entity facing the marker's front; item frames and paintings hang on the block behind it.
    /// `nbt` turns off the entity's own randomization, such as random armor, as it does for
    /// spawners; equipment and death loot don't.
    ///
    /// @param items items per equipment slot, keyed by the slot's name, like `head`
    public record SpawnEntity(
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
        public static final MapCodec<SpawnEntity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ENTITY.forGetter(SpawnEntity::entity),
                        NBT.forGetter(SpawnEntity::nbt),
                        EQUIPMENT.forGetter(SpawnEntity::equipment),
                        EQUIPMENT_DROP_CHANCE.forGetter(SpawnEntity::equipmentDropChance),
                        DEATH_LOOT_TABLE.forGetter(SpawnEntity::deathLootTable),
                        itemsCodec().forGetter(SpawnEntity::items))
                .apply(instance, SpawnEntity::new));

        public SpawnEntity {
            items = Map.copyOf(items);
        }

        public static SpawnEntity of(EntityType<?> entity) {
            return of(new EntityDataBuilder(entity));
        }

        /// death loot table becomes its own field, so it doesn't turn off the entity's randomization.
        public static SpawnEntity of(EntityDataBuilder entity) {
            var extraData = entity.build();
            var deathLootTable = extraData.read(EntityDataBuilder.DEATH_LOOT_TABLE_KEY, LootTable.KEY_CODEC);
            extraData.remove("id");
            extraData.remove(EntityDataBuilder.DEATH_LOOT_TABLE_KEY);
            return new SpawnEntity(
                    BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity.type()).orElseThrow(),
                    extraData.isEmpty() ? Optional.empty() : Optional.of(extraData),
                    Optional.empty(),
                    DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE,
                    deathLootTable,
                    Map.of());
        }

        public SpawnEntity withEquipment(ResourceKey<LootTable> lootTable) {
            return withEquipment(lootTable, equipmentDropChance);
        }

        public SpawnEntity withEquipment(ResourceKey<LootTable> lootTable, float dropChance) {
            return new SpawnEntity(entity, nbt, Optional.of(lootTable), dropChance, deathLootTable, items);
        }

        @Override
        public void apply(FixtureContext context) {
            var type = entityType(entity);
            if (type.isEmpty()) {
                return;
            }
            spawn(type.get(), nbt.orElse(null), context, spawned -> {
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

        // Only mobs roll equipment tables themselves; other living entities, like armor stands, get
        // the same slot rules but no drop chances, since they drop everything they hold anyway.
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
            return Stream.concat(Stream.of(entity), present(equipment, deathLootTable));
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

    /// A villager, or a zombie villager, of the given type and profession, or random ones. One given a
    /// profession keeps it without a workstation, as if it had traded once.
    public record SpawnVillager(
            Optional<ResourceKey<VillagerType>> villagerType,
            Optional<ResourceKey<VillagerProfession>> profession,
            boolean zombie) implements Fixture {
        public static final FixtureField<Optional<ResourceKey<VillagerType>>> TYPE = FixtureField.optional("villager_type", FieldType.registryKey(Registries.VILLAGER_TYPE));
        public static final FixtureField<Optional<ResourceKey<VillagerProfession>>> PROFESSION = FixtureField.optional("profession", FieldType.registryKey(Registries.VILLAGER_PROFESSION));
        public static final FixtureField<Boolean> ZOMBIE = FixtureField.withDefault("zombie", FieldType.bool(), false);
        public static final List<FixtureField<?>> FIELDS = List.of(TYPE, PROFESSION, ZOMBIE);
        public static final MapCodec<SpawnVillager> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        TYPE.forGetter(SpawnVillager::villagerType),
                        PROFESSION.forGetter(SpawnVillager::profession),
                        ZOMBIE.forGetter(SpawnVillager::zombie))
                .apply(instance, SpawnVillager::new));

        @Override
        public void apply(FixtureContext context) {
            spawn(zombie ? EntityType.ZOMBIE_VILLAGER : EntityType.VILLAGER, null, context, entity -> {
                if (!(entity instanceof VillagerDataHolder villager)) {
                    return;
                }
                var registries = context.level().registryAccess();
                var data = villager.getVillagerData();
                if (villagerType.isPresent()) {
                    data = data.withType(registries, villagerType.get());
                }
                if (profession.isPresent()) {
                    data = data.withProfession(registries, profession.get());
                }
                villager.setVillagerData(data);
                // A villager without experience or a workstation loses its profession within seconds.
                if (profession.isPresent()) {
                    switch (entity) {
                        case Villager adult when adult.getVillagerXp() == 0 -> adult.setVillagerXp(1);
                        case ZombieVillager zombie when zombie.getVillagerXp() == 0 -> zombie.setVillagerXp(1);
                        default -> {}
                    }
                }
            });
        }

        @Override
        public Stream<ResourceKey<?>> references() {
            return present(villagerType, profession);
        }

        @Override
        public FixtureType type() {
            return ASFixtures.VILLAGER.get();
        }
    }

    /// A spawner block, or a spawner minecart. Setting `max_block_light` or `max_sky_light` replaces
    /// the entity's own spawn rules with light ranges from 0 to those values, so a spawner can work
    /// where the entity would not spawn naturally. For settings shared by many spawners, or several
    /// entities, use a [ProfileSpawner].
    public record Spawner(
            Optional<ResourceKey<EntityType<?>>> entity,
            boolean minecart,
            int spawnCount,
            int maxNearbyEntities,
            int requiredPlayerRange,
            int spawnRange,
            int spawnDelay,
            int minSpawnDelay,
            int maxSpawnDelay,
            Optional<Integer> maxBlockLight,
            Optional<Integer> maxSkyLight) implements Fixture {
        public static final FixtureField<Optional<ResourceKey<EntityType<?>>>> ENTITY = FixtureField.optional("entity", FieldType.registryKey(Registries.ENTITY_TYPE));
        public static final FixtureField<Boolean> MINECART = FixtureField.withDefault("minecart", FieldType.bool(), false);
        public static final FixtureField<Integer> SPAWN_COUNT = FixtureField.withDefault("spawn_count", FieldType.integer(1, 64), SpawnerProfile.DEFAULT.spawnCount());
        public static final FixtureField<Integer> MAX_NEARBY_ENTITIES = FixtureField.withDefault("max_nearby_entities", FieldType.integer(1, 256), SpawnerProfile.DEFAULT.maxNearbyEntities());
        public static final FixtureField<Integer> REQUIRED_PLAYER_RANGE = FixtureField.withDefault("required_player_range", FieldType.integer(1, 128), SpawnerProfile.DEFAULT.requiredPlayerRange());
        public static final FixtureField<Integer> SPAWN_RANGE = FixtureField.withDefault("spawn_range", FieldType.integer(1, 32), SpawnerProfile.DEFAULT.spawnRange());
        public static final FixtureField<Integer> SPAWN_DELAY = FixtureField.withDefault("spawn_delay", FieldType.integer(0, SPAWN_DELAY_LIMIT), SpawnerProfile.DEFAULT.spawnDelay());
        public static final FixtureField<Integer> MIN_SPAWN_DELAY = FixtureField.withDefault("min_spawn_delay", FieldType.integer(0, SPAWN_DELAY_LIMIT), SpawnerProfile.DEFAULT.minSpawnDelay());
        public static final FixtureField<Integer> MAX_SPAWN_DELAY = FixtureField.withDefault("max_spawn_delay", FieldType.integer(0, SPAWN_DELAY_LIMIT), SpawnerProfile.DEFAULT.maxSpawnDelay());
        public static final FixtureField<Optional<Integer>> MAX_BLOCK_LIGHT = FixtureField.optional("max_block_light", FieldType.integer(0, 15));
        public static final FixtureField<Optional<Integer>> MAX_SKY_LIGHT = FixtureField.optional("max_sky_light", FieldType.integer(0, 15));
        public static final List<FixtureField<?>> FIELDS = List.of(
                ENTITY, MINECART, SPAWN_COUNT, MAX_NEARBY_ENTITIES, REQUIRED_PLAYER_RANGE, SPAWN_RANGE, SPAWN_DELAY,
                MIN_SPAWN_DELAY, MAX_SPAWN_DELAY, MAX_BLOCK_LIGHT, MAX_SKY_LIGHT);
        public static final MapCodec<Spawner> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ENTITY.forGetter(Spawner::entity),
                        MINECART.forGetter(Spawner::minecart),
                        SPAWN_COUNT.forGetter(Spawner::spawnCount),
                        MAX_NEARBY_ENTITIES.forGetter(Spawner::maxNearbyEntities),
                        REQUIRED_PLAYER_RANGE.forGetter(Spawner::requiredPlayerRange),
                        SPAWN_RANGE.forGetter(Spawner::spawnRange),
                        SPAWN_DELAY.forGetter(Spawner::spawnDelay),
                        MIN_SPAWN_DELAY.forGetter(Spawner::minSpawnDelay),
                        MAX_SPAWN_DELAY.forGetter(Spawner::maxSpawnDelay),
                        MAX_BLOCK_LIGHT.forGetter(Spawner::maxBlockLight),
                        MAX_SKY_LIGHT.forGetter(Spawner::maxSkyLight))
                .apply(instance, Spawner::new));

        /// A spawner block for `entity` with vanilla's settings.
        public static Spawner of(EntityType<?> entity) {
            var defaults = SpawnerProfile.DEFAULT;
            return new Spawner(Optional.of(BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity).orElseThrow()), false,
                    defaults.spawnCount(), defaults.maxNearbyEntities(), defaults.requiredPlayerRange(), defaults.spawnRange(),
                    defaults.spawnDelay(), defaults.minSpawnDelay(), defaults.maxSpawnDelay(), Optional.empty(), Optional.empty());
        }

        @Override
        public void apply(FixtureContext context) {
            var spawned = entity.flatMap(Fixtures::entityType).orElse(null);
            var profile = SpawnerProfile.builder()
                    .spawnCount(spawnCount)
                    .maxNearbyEntities(maxNearbyEntities)
                    .requiredPlayerRange(requiredPlayerRange)
                    .spawnRange(spawnRange)
                    .spawnDelay(spawnDelay)
                    .minSpawnDelay(minSpawnDelay)
                    // The spawner draws its delay between the two, so the maximum can't be lower.
                    .maxSpawnDelay(Math.max(minSpawnDelay, maxSpawnDelay))
                    .build();
            placeSpawner(context, minecart, (_, spawner) -> {
                SpawnerAccess.applyWithSpawnDelay(spawner, profile);
                if (spawned != null) {
                    // No level: the spawner would read the block back from the server level to notify
                    // clients, which from a world generation thread waits on the main thread, which is
                    // waiting on this chunk. Clients get the spawner with its chunk anyway.
                    spawner.setEntityId(spawned, null, context.random(), context.actionBlockPos());
                }
                var next = SpawnerAccess.nextSpawnData(spawner);
                if (next != null && (maxBlockLight.isPresent() || maxSkyLight.isPresent())) {
                    var rules = new SpawnData.CustomSpawnRules(lightRange(maxBlockLight), lightRange(maxSkyLight));
                    SpawnerAccess.setNextSpawnData(spawner, new SpawnData(next.entityToSpawn(), Optional.of(rules), next.equipment()));
                }
            });
        }

        private static InclusiveRange<Integer> lightRange(Optional<Integer> max) {
            return new InclusiveRange<>(0, max.orElse(15));
        }

        @Override
        public Stream<ResourceKey<?>> references() {
            return present(entity);
        }

        @Override
        public FixtureType type() {
            return ASFixtures.SPAWNER.get();
        }
    }

    /// A spawner that takes its settings from a [SpawnerProfile] every time it loads.
    public record ProfileSpawner(ResourceKey<SpawnerProfile> profile, boolean minecart) implements Fixture {
        public static final FixtureField<ResourceKey<SpawnerProfile>> PROFILE = FixtureField.required("profile", FieldType.registryKey(ASRegistries.SPAWNER_PROFILE));
        public static final FixtureField<Boolean> MINECART = FixtureField.withDefault("minecart", FieldType.bool(), false);
        public static final List<FixtureField<?>> FIELDS = List.of(PROFILE, MINECART);
        public static final MapCodec<ProfileSpawner> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        PROFILE.forGetter(ProfileSpawner::profile),
                        MINECART.forGetter(ProfileSpawner::minecart))
                .apply(instance, ProfileSpawner::new));

        @Override
        public void apply(FixtureContext context) {
            var attachment = new SpawnerProfileAttachment(profile.identifier());
            placeSpawner(context, minecart, (holder, spawner) -> {
                holder.setData(ASAttachments.SPAWNER_PROFILE, attachment);
                // Applied now as well as on every later load: a freshly generated block entity is not reloaded.
                SpawnerProfiles.resolve(attachment, context.level().registryAccess())
                        .ifPresent(resolved -> SpawnerAccess.applyWithSpawnDelay(spawner, resolved));
            });
        }

        @Override
        public Stream<ResourceKey<?>> references() {
            return Stream.of(profile);
        }

        @Override
        public FixtureType type() {
            return ASFixtures.SPAWNER_PROFILE.get();
        }
    }

    /// A trial spawner using configs from the `trial_spawner` registry, such as vanilla's
    /// `minecraft:trial_chamber/melee/zombie/normal`. Without `ominous_config`, an ominous trial uses
    /// `normal_config` as well.
    public record TrialSpawner(
            ResourceKey<TrialSpawnerConfig> normalConfig,
            Optional<ResourceKey<TrialSpawnerConfig>> ominousConfig,
            int targetCooldownLength,
            int requiredPlayerRange) implements Fixture {
        public static final FixtureField<ResourceKey<TrialSpawnerConfig>> NORMAL_CONFIG = FixtureField.required("normal_config", FieldType.registryKey(Registries.TRIAL_SPAWNER_CONFIG));
        public static final FixtureField<Optional<ResourceKey<TrialSpawnerConfig>>> OMINOUS_CONFIG = FixtureField.optional("ominous_config", FieldType.registryKey(Registries.TRIAL_SPAWNER_CONFIG));
        public static final FixtureField<Integer> TARGET_COOLDOWN_LENGTH = FixtureField.withDefault("target_cooldown_length", FieldType.integer(0, Integer.MAX_VALUE), FullConfig.DEFAULT.targetCooldownLength());
        public static final FixtureField<Integer> REQUIRED_PLAYER_RANGE = FixtureField.withDefault("required_player_range", FieldType.integer(1, 128), FullConfig.DEFAULT.requiredPlayerRange());
        public static final List<FixtureField<?>> FIELDS = List.of(NORMAL_CONFIG, OMINOUS_CONFIG, TARGET_COOLDOWN_LENGTH, REQUIRED_PLAYER_RANGE);
        public static final MapCodec<TrialSpawner> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        NORMAL_CONFIG.forGetter(TrialSpawner::normalConfig),
                        OMINOUS_CONFIG.forGetter(TrialSpawner::ominousConfig),
                        TARGET_COOLDOWN_LENGTH.forGetter(TrialSpawner::targetCooldownLength),
                        REQUIRED_PLAYER_RANGE.forGetter(TrialSpawner::requiredPlayerRange))
                .apply(instance, TrialSpawner::new));

        /// A trial spawner with vanilla's cooldown and player range.
        public TrialSpawner(ResourceKey<TrialSpawnerConfig> normalConfig, ResourceKey<TrialSpawnerConfig> ominousConfig) {
            this(normalConfig, Optional.of(ominousConfig), FullConfig.DEFAULT.targetCooldownLength(), FullConfig.DEFAULT.requiredPlayerRange());
        }

        @Override
        public void apply(FixtureContext context) {
            var registries = context.level().registryAccess();
            var configs = registries.lookupOrThrow(Registries.TRIAL_SPAWNER_CONFIG);
            var normal = config(configs, normalConfig);
            var ominous = ominousConfig.isPresent() ? config(configs, ominousConfig.get()) : normal;
            if (normal.isEmpty() || ominous.isEmpty()) {
                return;
            }
            context.placeBlock(Blocks.TRIAL_SPAWNER.defaultBlockState());
            if (!(context.level().getBlockEntity(context.actionBlockPos()) instanceof TrialSpawnerBlockEntity spawner)) {
                return;
            }
            configure(spawner, new FullConfig(normal.get(), ominous.get(), targetCooldownLength, requiredPlayerRange), registries);
        }

        // The trial spawner has no setter for its config: it loads it, as from a template's block entity data.
        static void configure(TrialSpawnerBlockEntity spawner, FullConfig config, HolderLookup.Provider registries) {
            var data = FullConfig.MAP_CODEC.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), config).getOrThrow();
            spawner.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, (CompoundTag) data));
        }

        private static Optional<? extends Holder<TrialSpawnerConfig>> config(HolderLookup<TrialSpawnerConfig> configs, ResourceKey<TrialSpawnerConfig> key) {
            var config = configs.get(key);
            if (config.isEmpty()) {
                ASLog.warn("fixture trial spawner config {} is not registered", key.identifier());
            }
            return config;
        }

        @Override
        public Stream<ResourceKey<?>> references() {
            return Stream.concat(Stream.of(normalConfig), present(ominousConfig));
        }

        @Override
        public FixtureType type() {
            return ASFixtures.TRIAL_SPAWNER.get();
        }
    }

    public record Vault(
            boolean ominous,
            Optional<ResourceKey<LootTable>> lootTable,
            Optional<ItemStackTemplate> keyItem,
            Optional<ResourceKey<LootTable>> displayLootTable) implements Fixture {
        // Vanilla's defaults, whose config constant isn't public.
        private static final double VAULT_ACTIVATION_RANGE = 4.0;
        private static final double VAULT_DEACTIVATION_RANGE = 4.5;

        public static final FixtureField<Boolean> OMINOUS = FixtureField.withDefault("ominous", FieldType.bool(), false);
        public static final FixtureField<Optional<ResourceKey<LootTable>>> LOOT_TABLE = FixtureField.optional("loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
        public static final FixtureField<Optional<ItemStackTemplate>> KEY_ITEM = FixtureField.optional("key_item", FieldType.item());
        public static final FixtureField<Optional<ResourceKey<LootTable>>> DISPLAY_LOOT_TABLE = FixtureField.optional("display_loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
        public static final List<FixtureField<?>> FIELDS = List.of(OMINOUS, LOOT_TABLE, KEY_ITEM, DISPLAY_LOOT_TABLE);
        public static final MapCodec<Vault> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        OMINOUS.forGetter(Vault::ominous),
                        LOOT_TABLE.forGetter(Vault::lootTable),
                        KEY_ITEM.forGetter(Vault::keyItem),
                        DISPLAY_LOOT_TABLE.forGetter(Vault::displayLootTable))
                .apply(instance, Vault::new));

        public Vault(boolean ominous) {
            this(ominous, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public Vault(boolean ominous, ResourceKey<LootTable> lootTable) {
            this(ominous, Optional.of(lootTable), Optional.empty(), Optional.empty());
        }

        @Override
        public void apply(FixtureContext context) {
            context.placeBlock(Blocks.VAULT.defaultBlockState().setValue(VaultBlock.OMINOUS, ominous));
            if (context.level().getBlockEntity(context.actionBlockPos()) instanceof VaultBlockEntity vault) {
                // Marked for tests only, but it's the vault's one way to take a config other than loading it from a tag.
                vault.setConfig(config());
                vault.setChanged();
            }
        }

        VaultConfig config() {
            var defaultLootTable = ominous ? BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS : BuiltInLootTables.TRIAL_CHAMBERS_REWARD;
            var defaultKey = ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;
            return new VaultConfig(
                    lootTable.orElse(defaultLootTable),
                    VAULT_ACTIVATION_RANGE,
                    VAULT_DEACTIVATION_RANGE,
                    keyItem.map(ItemStackTemplate::create).orElseGet(() -> new ItemStack(defaultKey)),
                    displayLootTable);
        }

        @Override
        public Stream<ResourceKey<?>> references() {
            return present(lootTable, displayLootTable);
        }

        @Override
        public FixtureType type() {
            return ASFixtures.VAULT.get();
        }
    }

    public record PlaceFeature(ResourceKey<ConfiguredFeature<?, ?>> feature) implements Fixture {
        public static final FixtureField<ResourceKey<ConfiguredFeature<?, ?>>> FEATURE = FixtureField.required("feature", FieldType.registryKey(Registries.CONFIGURED_FEATURE));
        public static final List<FixtureField<?>> FIELDS = List.of(FEATURE);
        public static final MapCodec<PlaceFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        FEATURE.forGetter(PlaceFeature::feature))
                .apply(instance, PlaceFeature::new));

        @Override
        public void apply(FixtureContext context) {
            context.level().registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(feature)
                    .map(Holder::value)
                    .ifPresentOrElse(
                            value -> value.place(context.level(), context.generator(), context.random(), context.actionBlockPos()),
                            () -> ASLog.warn("fixture feature {} is not registered", feature.identifier()));
        }

        @Override
        public Stream<ResourceKey<?>> references() {
            return Stream.of(feature);
        }

        @Override
        public FixtureType type() {
            return ASFixtures.FEATURE.get();
        }
    }

    /// Suspicious sand or gravel, holding an item from `loot_table` when set.
    public record Archaeology(Optional<ResourceKey<LootTable>> lootTable, BlockState block) implements Fixture {
        public static final FixtureField<Optional<ResourceKey<LootTable>>> LOOT_TABLE = FixtureField.optional("loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
        public static final FixtureField<BlockState> BLOCK = FixtureField.withDefault("block", FieldType.blockState(), Blocks.SUSPICIOUS_SAND.defaultBlockState());
        public static final List<FixtureField<?>> FIELDS = List.of(LOOT_TABLE, BLOCK);
        public static final MapCodec<Archaeology> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        LOOT_TABLE.forGetter(Archaeology::lootTable),
                        BLOCK.forGetter(Archaeology::block))
                .apply(instance, Archaeology::new));

        @Override
        public void apply(FixtureContext context) {
            context.placeBlock(block);
            if (context.level().getBlockEntity(context.actionBlockPos()) instanceof BrushableBlockEntity brushable) {
                lootTable.ifPresent(loot -> brushable.setLootTable(loot, context.random().nextLong()));
            }
        }

        @Override
        public Stream<ResourceKey<?>> references() {
            return present(lootTable);
        }

        @Override
        public FixtureType type() {
            return ASFixtures.ARCHAEOLOGY.get();
        }
    }

    /// Grows a pillar from the action position down to the ground, like a leg or a support beam. Uses
    /// `state` when set, otherwise repeats the block directly above, so the marker goes right under the
    /// bottom block of the leg; one that [can't be repeated][Foundation#canRepeat(BlockState)] grows
    /// nothing. Stops like a [Foundation] column: at the first block that is not air, fluid or
    /// replaceable, or after `max_depth` blocks.
    public record ExtendDown(Optional<BlockState> state, int maxDepth) implements Fixture {
        public static final FixtureField<Optional<BlockState>> STATE = FixtureField.optional("state", FieldType.blockState());
        public static final FixtureField<Integer> MAX_DEPTH = FixtureField.withDefault("max_depth", FieldType.integer(1, 512), Foundation.DEFAULT_MAX_DEPTH);
        public static final List<FixtureField<?>> FIELDS = List.of(STATE, MAX_DEPTH);
        public static final MapCodec<ExtendDown> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        STATE.forGetter(ExtendDown::state),
                        MAX_DEPTH.forGetter(ExtendDown::maxDepth))
                .apply(instance, ExtendDown::new));

        @Override
        public void apply(FixtureContext context) {
            var top = context.actionBlockPos();
            var fill = state.orElseGet(() -> context.level().getBlockState(top.above()));
            if (!Foundation.isFillable(fill) && (state.isPresent() || Foundation.canRepeat(fill))) {
                Foundation.fillDown(context.level(), top, fill, maxDepth, context.pieceBounds());
            }
        }

        @Override
        public FixtureType type() {
            return ASFixtures.EXTEND_DOWN.get();
        }
    }

    // --- shared plumbing ------------------------------------------------------------------------

    private static final Map<Block, Optional<Class<?>>> BLOCK_ENTITY_CLASSES = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, Boolean> HANGING_TYPES = new ConcurrentHashMap<>();

    private static boolean isLootContainer(BlockState state) {
        return hasBlockEntity(state, RandomizableContainer.class);
    }

    private static boolean isContainer(BlockState state) {
        return hasBlockEntity(state, Container.class);
    }

    // Asks the block entity itself, so modded containers count too.
    private static boolean hasBlockEntity(BlockState state, Class<?> type) {
        return BLOCK_ENTITY_CLASSES.computeIfAbsent(state.getBlock(), block -> {
            try {
                return block instanceof EntityBlock entityBlock
                        ? Optional.ofNullable(entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState())).map(Object::getClass)
                        : Optional.empty();
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }).filter(type::isAssignableFrom).isPresent();
    }

    @SafeVarargs
    private static Stream<ResourceKey<?>> present(Optional<? extends ResourceKey<?>>... keys) {
        return Arrays.stream(keys).flatMap(Optional::stream);
    }

    // The entity registry falls back to pigs for unknown ids, so a mistyped id would spawn one.
    private static Optional<EntityType<?>> entityType(ResourceKey<EntityType<?>> key) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(key);
        if (type.isEmpty()) {
            ASLog.warn("fixture entity {} is not registered", key.identifier());
        }
        return type;
    }

    // Spawns a persistent entity the way vanilla structures do, facing the marker's front. Like a
    // spawner, only an entity without extra data gets its own randomization, such as armor.
    // `configure` runs before the entity joins the level: a generating chunk saves it on arrival,
    // so later changes would be lost.
    private static void spawn(EntityType<?> type, @Nullable CompoundTag extraData, FixtureContext context, Consumer<Entity> configure) {
        WorldGenLevel level = context.level();
        Vec3 pos = context.actionPos();
        float yaw = FixtureBlock.horizontalFacing(context.markerState()).toYRot();
        boolean hanging = isHanging(type, level.getLevel());
        CompoundTag data = hanging ? hangingData(extraData, context) : extraData;
        EntityProcessor place = entity -> {
            // A hanging entity's position and direction come from its data.
            if (!hanging) {
                entity.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
            }
            if (entity instanceof LivingEntity living) {
                living.setYHeadRot(yaw);
                living.setYBodyRot(yaw);
            }
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            return entity;
        };
        boolean configured = data != null && !data.isEmpty();
        Entity entity = configured
                ? EntityType.loadEntityRecursive(type, data, level.getLevel(), EntitySpawnReason.STRUCTURE, place)
                : type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (entity == null) {
            return;
        }
        if (!configured) {
            place.process(entity);
            if (entity instanceof Mob mob) {
                EventHooks.finalizeMobSpawn(
                        mob, level, level.getCurrentDifficultyAt(context.actionBlockPos()), EntitySpawnReason.STRUCTURE, null);
                if (mob.isSpawnCancelled()) {
                    return;
                }
            }
        }
        configure.accept(entity);
        level.addFreshEntityWithPassengers(entity);
    }

    // Hanging entities, such as item frames and paintings, only take their direction while loading.
    // They hang on the block behind the marker, facing its front; paintings can only face sideways.
    private static CompoundTag hangingData(@Nullable CompoundTag extraData, FixtureContext context) {
        var data = extraData == null ? new CompoundTag() : extraData.copy();
        var blockPos = context.actionBlockPos();
        data.store("Pos", Vec3.CODEC, Vec3.atCenterOf(blockPos));
        data.store("block_pos", BlockPos.CODEC, blockPos);
        if (!data.contains("Facing")) {
            data.putByte("Facing", (byte) FixtureBlock.front(context.markerState()).get3DDataValue());
        }
        if (!data.contains("facing")) {
            data.putByte("facing", (byte) FixtureBlock.horizontalFacing(context.markerState()).get2DDataValue());
        }
        return data;
    }

    // EntityType#getBaseClass is always Entity, so an instance tells instead.
    private static boolean isHanging(EntityType<?> type, Level level) {
        return HANGING_TYPES.computeIfAbsent(type, _ -> type.create(level, EntitySpawnReason.STRUCTURE) instanceof HangingEntity);
    }

    // Places a spawner block, or spawns a spawner minecart, and hands it and its base spawner to configure.
    private static void placeSpawner(
            FixtureContext context, boolean minecart, BiConsumer<IAttachmentHolder, BaseSpawner> configure) {
        if (minecart) {
            spawn(EntityType.SPAWNER_MINECART, null, context, entity -> {
                if (entity instanceof MinecartSpawner cart) {
                    configure.accept(cart, cart.getSpawner());
                }
            });
            return;
        }
        context.placeBlock(Blocks.SPAWNER.defaultBlockState());
        if (context.level().getBlockEntity(context.actionBlockPos()) instanceof SpawnerBlockEntity spawner) {
            configure.accept(spawner, spawner.getSpawner());
        }
    }
}
