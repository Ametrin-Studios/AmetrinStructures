package com.ametrin.structures.processor;

import com.ametrin.structures.registry.ASProcessors;
import com.ametrin.structures.util.ASLog;
import com.ametrin.structures.util.RegistryConditionContext;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Runs `processors` only when all NeoForge `conditions` pass, e.g. `neoforge:mod_loaded`. Otherwise it changes nothing.
///
/// The processors are only read once the conditions pass, so they can use another mod's blocks and processors.
/// `processors` takes a processor list id or the processors themselves, like anywhere else.
public final class ConditionalProcessor extends StructureProcessor {
    public static final MapCodec<ConditionalProcessor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ICondition.LIST_CODEC.fieldOf("conditions").forGetter(processor -> processor.conditions),
                    Codec.PASSTHROUGH.fieldOf("processors").forGetter(processor -> processor.processors))
            .apply(instance, ConditionalProcessor::new));

    private final List<ICondition> conditions;
    private final Dynamic<?> processors;
    // Decided on first use, when the world's registries and tags are there.
    private volatile @Nullable List<StructureProcessor> resolved;

    public ConditionalProcessor(List<ICondition> conditions, Dynamic<?> processors) {
        this.conditions = List.copyOf(conditions);
        this.processors = processors;
    }

    public static ConditionalProcessor of(List<ICondition> conditions, ResourceKey<StructureProcessorList> processors) {
        return new ConditionalProcessor(conditions, new Dynamic<JsonElement>(JsonOps.INSTANCE, JsonOps.INSTANCE.createString(processors.identifier().toString())));
    }

    /// Only for processors of mods that are always there. Use the processor list overload otherwise.
    public static ConditionalProcessor of(List<ICondition> conditions, List<StructureProcessor> processors) {
        var encoded = StructureProcessorType.SINGLE_CODEC.listOf().encodeStart(JsonOps.INSTANCE, processors).getOrThrow();
        return new ConditionalProcessor(conditions, new Dynamic<>(JsonOps.INSTANCE, encoded));
    }

    /// The processors that run, empty if the conditions fail or the processors don't read.
    List<StructureProcessor> processors(HolderLookup.Provider registries) {
        var resolved = this.resolved;
        if (resolved == null) {
            resolved = resolve(registries);
            this.resolved = resolved;
        }
        return resolved;
    }

    private List<StructureProcessor> resolve(HolderLookup.Provider registries) {
        var context = new RegistryConditionContext(registries);
        if (!conditions.stream().allMatch(condition -> condition.test(context))) {
            return List.of();
        }
        return decode(processors, registries)
                .resultOrPartial(error -> ASLog.warn("conditional processor's processors don't read: {}", error))
                .map(list -> list.value().list())
                .orElseGet(List::of);
    }

    private static <T> DataResult<Holder<StructureProcessorList>> decode(Dynamic<T> data, HolderLookup.Provider registries) {
        return StructureProcessorType.LIST_CODEC.parse(RegistryOps.create(data.getOps(), registries), data.getValue());
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo process(
            LevelReader level, BlockPos origin, BlockPos pivot,
            StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings, @Nullable StructureTemplate template) {
        for (var processor : processors(level.registryAccess())) {
            current = processor.process(level, origin, pivot, original, current, settings, template);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @Override
    public StructureTemplate.@Nullable StructureEntityInfo processEntity(
            LevelReader level, BlockPos origin,
            StructureTemplate.StructureEntityInfo original, StructureTemplate.StructureEntityInfo current,
            StructurePlaceSettings settings, StructureTemplate template) {
        for (var processor : processors(level.registryAccess())) {
            current = processor.processEntity(level, origin, original, current, settings, template);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level, BlockPos origin, BlockPos pivot,
            List<StructureTemplate.StructureBlockInfo> original, List<StructureTemplate.StructureBlockInfo> current,
            StructurePlaceSettings settings) {
        for (var processor : processors(level.registryAccess())) {
            current = processor.finalizeProcessing(level, origin, pivot, original, current, settings);
        }
        return current;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ASProcessors.CONDITIONAL.get();
    }
}
