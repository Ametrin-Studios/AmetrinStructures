package com.ametrin.structures.fixture;

import com.ametrin.structures.util.ASLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Helpers for writing fixtures.
public final class Fixtures {
    private static final Map<Block, Optional<Class<?>>> BLOCK_ENTITY_CLASSES = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, Boolean> HANGING_TYPES = new ConcurrentHashMap<>();

    private Fixtures() {}

    /// Whether the block's entity takes a loot table, modded ones included.
    public static boolean isLootContainer(BlockState state) {
        return hasBlockEntity(state, RandomizableContainer.class);
    }

    /// Whether the block's entity has an inventory, modded ones included.
    public static boolean isContainer(BlockState state) {
        return hasBlockEntity(state, Container.class);
    }

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

    /// The keys that are set, for [Fixture#references()].
    @SafeVarargs
    public static Stream<ResourceKey<?>> present(Optional<? extends ResourceKey<?>>... keys) {
        return Arrays.stream(keys).flatMap(Optional::stream);
    }

    /// Logs unregistered ones: the entity registry falls back to pigs for unknown ids.
    public static Optional<EntityType<?>> entityType(ResourceKey<EntityType<?>> key) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(key);
        if (type.isEmpty()) {
            ASLog.warn("fixture entity {} is not registered", key.identifier());
        }
        return type;
    }

    /// Spawns a persistent entity the way vanilla structures do, facing the marker's front; hanging
    /// entities hang on the block behind it. Like a spawner, only an entity without extra data gets its own
    /// randomization, such as armor. `configure` runs before the entity joins the level, since a generating
    /// chunk saves it on arrival.
    public static void spawnEntity(EntityType<?> type, @Nullable CompoundTag extraData, FixtureContext context, Consumer<Entity> configure) {
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

    /// Places a spawner block, or spawns a spawner minecart, and hands it and its spawner to `configure`.
    public static void placeSpawner(FixtureContext context, boolean minecart, BiConsumer<IAttachmentHolder, BaseSpawner> configure) {
        if (minecart) {
            spawnEntity(EntityType.SPAWNER_MINECART, null, context, entity -> {
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
