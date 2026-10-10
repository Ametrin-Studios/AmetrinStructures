package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.util.ASLog;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/// Any block with an inventory, like a brewing stand or a furnace, filled from `loot_table` when the
/// structure generates. Unlike [LootContainerFixture], the loot is rolled right away instead of when a
/// player first opens it, so it works for blocks that don't support loot tables. Each item goes into an
/// empty slot that accepts it, preferring the slot that accepts the fewest kinds of items. That way
/// blaze powder ends up in a brewing stand's fuel slot. Ties are broken randomly, and items that don't
/// fit anywhere are dropped. The loot table runs during world generation, so don't use functions that
/// search the world, like exploration maps.
///
/// A brewing stand's bottles follow the loot. The stand sets them itself once it ticks, so `block`
/// leaves them out.
public record FillContainerFixture(ResourceKey<LootTable> lootTable, BlockState block) implements Fixture {
    private static final Map<BlockEntityType<?>, int[]> SLOT_BREADTH = new ConcurrentHashMap<>();

    public static final FixtureField<ResourceKey<LootTable>> LOOT_TABLE = FixtureField.required("loot_table", FieldType.registryKey(Registries.LOOT_TABLE));
    public static final FixtureField<BlockState> BLOCK = FixtureField.withDefault(
            "block",
            BlockStateMerging.mergedBlockState(List.of(BrewingStandBlock.HAS_BOTTLE))
                    .validated(Fixtures::isContainer, "the block must have an inventory"),
            Blocks.BREWING_STAND.defaultBlockState());
    public static final List<FixtureField<?>> FIELDS = List.of(LOOT_TABLE, BLOCK);
    public static final MapCodec<FillContainerFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    LOOT_TABLE.forGetter(FillContainerFixture::lootTable),
                    BLOCK.forGetter(FillContainerFixture::block))
            .apply(instance, FillContainerFixture::new));

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

    // A block entity that's still generating has no level, but some need one to decide if an item fits.
    // A brewing stand rejects potions without one, so a copy with the level decides instead. The copy is
    // never placed, so giving it the level doesn't affect the world.
    private static Container judge(BlockEntity blockEntity, Level level) {
        if (blockEntity.hasLevel() || !(blockEntity.getType().create(blockEntity.getBlockPos(), blockEntity.getBlockState()) instanceof Container copy)) {
            return (Container) blockEntity;
        }
        ((BlockEntity) copy).setLevel(level);
        return copy;
    }

    /// Puts each stack into empty slots of `container`, splitting it to fit. `judge`, a container of the
    /// same kind, decides which slots accept it. The [narrowest][#breadth(Container)] accepting slot is
    /// used, so blaze powder goes into a brewing stand's fuel slot instead of the ingredient slot. Ties
    /// are broken randomly.
    ///
    /// @return what didn't fit
    // Items are set as if inside a transaction. That skips side effects like a chiseled bookshelf
    // updating its block, which would need a level the generating block entity doesn't have.
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
                    // Some containers reject items in setItem itself, like a chiseled bookshelf with anything but books.
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
        // Cached per block entity type. It's only used to order slots, so if a datapack changes what a slot
        // accepts, like brewing ingredients, the order is a bit off at worst.
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
