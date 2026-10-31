package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.fixture.FixtureGeneration;
import com.ametrin.structures.foam.RemoveFoamProcessor;
import com.ametrin.structures.registry.ASPoolElements;
import com.ametrin.structures.structure.Foundation;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

import java.util.List;
import java.util.Optional;

public class ExtendedSinglePoolElement extends SinglePoolElement {
    static final Holder<StructureProcessorList> NO_PROCESSORS = Holder.direct(new StructureProcessorList(List.of()));
    private static final ResourceKey<StructureProcessorList> EMPTY_PROCESSORS = ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty"));

    public static final MapCodec<ExtendedSinglePoolElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    templateCodec(),
                    // Unlike vanilla's element, it can be left out when there are none.
                    StructureProcessorType.LIST_CODEC.optionalFieldOf("processors")
                            .<Holder<StructureProcessorList>>xmap(processors -> processors.orElse(NO_PROCESSORS), processors -> hasNone(processors) ? Optional.empty() : Optional.of(processors))
                            .forGetter(element -> element.processors),
                    projectionCodec(),
                    overrideLiquidSettingsCodec(),
                    Codec.BOOL.optionalFieldOf("process_foam", true).forGetter(element -> element.processFoam),
                    Foundation.CODEC.optionalFieldOf("foundation").forGetter(element -> element.foundation))
            .apply(instance, ExtendedSinglePoolElement::new));

    private final boolean processFoam;
    private final Optional<Foundation> foundation;

    // Doesn't read a registered list's contents, since the registry may not be bound yet in datagen.
    private static boolean hasNone(Holder<StructureProcessorList> processors) {
        return processors.is(EMPTY_PROCESSORS) || processors.kind() == Holder.Kind.DIRECT && processors.value().list().isEmpty();
    }

    public ExtendedSinglePoolElement(
            Either<Identifier, StructureTemplate> template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection,
            Optional<LiquidSettings> overrideLiquidSettings,
            boolean processFoam,
            Optional<Foundation> foundation) {
        super(template, processors, projection, overrideLiquidSettings);
        this.processFoam = processFoam;
        this.foundation = foundation;
    }

    @Override
    public boolean place(
            StructureTemplateManager templates,
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            BlockPos position,
            BlockPos referencePos,
            Rotation rotation,
            BoundingBox chunkBox,
            RandomSource random,
            LiquidSettings liquidSettings,
            boolean keepJigsaws) {
        if (!super.place(templates, level, structureManager, generator, position, referencePos, rotation, chunkBox, random, liquidSettings, keepJigsaws)) {
            return false;
        }
        var settings = getSettings(rotation, chunkBox, liquidSettings, keepJigsaws);
        FixtureGeneration.process(null, template.map(templates::getOrCreate, loaded -> loaded), position, settings, chunkBox, random, level, generator);
        foundation.ifPresent(value -> value.placeUnder(level, getBoundingBox(templates, position, rotation), chunkBox));
        return true;
    }

    @Override
    protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox chunkBB, LiquidSettings liquidSettings, boolean keepJigsaws) {
        // Added here instead of in the constructor, so the element is saved to JSON unchanged and doesn't touch its processor holder before the registry is bound.
        var settings = super.getSettings(rotation, chunkBB, liquidSettings, keepJigsaws);
        return processFoam ? RemoveFoamProcessor.addDefaultIfAbsent(settings) : settings;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return ASPoolElements.SINGLE.get();
    }
}
