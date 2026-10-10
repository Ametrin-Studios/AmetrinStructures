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

/// Any block with an inventory, such as a brewing stand or a furnace, filled from `loot_table` as the
/// structure generates. Unlike a [LootContainerFixture] the loot is drawn right away, not when a player
/// first opens it, so it works for blocks that don't take a loot table. Each item goes into an
/// empty slot that accepts it, the one that accepts the fewest kinds of items, so blaze powder goes
/// to a brewing stand's fuel slot; among equal slots the choice is random. What fits nowhere is
/// left out. The loot table runs during
/// world generation, so keep it free of functions that search the world, such as exploration maps.
///
/// A brewing stand's bottles follow the loot: the stand sets them itself once it ticks, so `block`
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
