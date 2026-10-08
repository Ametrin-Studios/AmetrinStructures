package com.ametrin.structures.processor;

import com.ametrin.structures.registry.ASProcessors;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/// Replaces every block matching `condition` with `change_to` at probability `chance`, rolled once per
/// position. With `preserve_state`, properties both blocks share carry over, and so does block entity
/// data, which is otherwise only kept when the block stays the same.
public class ReplaceBlockProcessor extends StructureProcessor {
    public static final MapCodec<ReplaceBlockProcessor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Condition.CODEC.fieldOf("condition").forGetter(p -> p.condition),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(p -> p.chance),
                    BlockState.CODEC.fieldOf("change_to").forGetter(p -> p.changeTo),
                    Codec.BOOL.optionalFieldOf("preserve_state", false).forGetter(p -> p.preserveState))
            .apply(instance, ReplaceBlockProcessor::new));

    private final Condition condition;
    private final float chance;
    private final BlockState changeTo;
    private final boolean preserveState;

    public ReplaceBlockProcessor(Condition condition, float chance, BlockState changeTo, boolean preserveState) {
        this.condition = condition;
        this.chance = chance;
        this.changeTo = changeTo;
        this.preserveState = preserveState;
    }

    public ReplaceBlockProcessor(Condition condition, float chance, BlockState changeTo) {
        this(condition, chance, changeTo, false);
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo process(
            LevelReader level,
            BlockPos targetPosition,
            BlockPos referencePos,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings,
            @Nullable StructureTemplate template) {
        if (!condition.test(current.state()) || settings.getRandom(current.pos()).nextFloat() >= chance) {
            return current;
        }
        // Rotation and mirroring apply after processing.
        BlockState state = preserveState ? mergeProperties(current.state(), changeTo) : changeTo;
        // Another block's block entity data would load into the new block's block entity.
        var keepData = preserveState || state.is(current.state().getBlock());
        return new StructureTemplate.StructureBlockInfo(current.pos(), state, keepData ? current.nbt() : null);
    }

    private static BlockState mergeProperties(BlockState source, BlockState target) {
        var result = target;
        for (var property : source.getProperties()) {
            result = copyProperty(source, result, property);
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState source, BlockState target, Property<T> property) {
        return target.hasProperty(property) ? target.setValue(property, source.getValue(property)) : target;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ASProcessors.REPLACE_BLOCK.get();
    }

    public sealed interface Condition extends Predicate<BlockState> {
        Codec<Condition> CODEC = Codec.either(
                        Codec.either(TagKey.hashedCodec(Registries.BLOCK), BuiltInRegistries.BLOCK.byNameCodec()),
                        BlockState.CODEC)
                .xmap(
                        either -> either.map(name -> name.map(InTag::new, IsBlock::new), IsState::new),
                        condition -> switch (condition) {
                            case InTag(TagKey<Block> tag) -> Either.left(Either.left(tag));
                            case IsBlock(Block block) -> Either.left(Either.right(block));
                            case IsState(BlockState state) -> Either.right(state);
                        });

        static Condition of(Block block) {
            return new IsBlock(block);
        }

        static Condition of(TagKey<Block> tag) {
            return new InTag(tag);
        }

        static Condition of(BlockState state) {
            return new IsState(state);
        }

        record IsBlock(Block block) implements Condition {
            @Override
            public boolean test(BlockState state) {
                return state.is(block);
            }
        }

        record InTag(TagKey<Block> tag) implements Condition {
            @Override
            public boolean test(BlockState state) {
                return state.is(tag);
            }
        }

        record IsState(BlockState state) implements Condition {
            @Override
            public boolean test(BlockState other) {
                return other == state;
            }
        }
    }
}
