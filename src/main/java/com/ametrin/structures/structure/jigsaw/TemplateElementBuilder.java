package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.Foundation;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/// The settings of a template placed as an [ExtendedSinglePoolElement], for elements of [JigsawPools] and templates of modular structures alike.
public abstract class TemplateElementBuilder<B extends TemplateElementBuilder<B>> {
    private final HolderGetter<StructureProcessorList> processorsGetter;
    @Nullable
    private Holder<StructureProcessorList> processors;
    private Optional<LiquidSettings> liquidSettings = Optional.empty();
    private boolean processFoam = true;
    private Optional<Foundation> foundation = Optional.empty();

    protected TemplateElementBuilder(HolderGetter<StructureProcessorList> processorsGetter) {
        this.processorsGetter = processorsGetter;
    }

    /// Replaces the structure's processors. Add [com.ametrin.structures.structure.InlineFromStructureProcessor#INSTANCE] to keep them.
    public B processors(ResourceKey<StructureProcessorList> processors) {
        return processors(processorsGetter.getOrThrow(processors));
    }

    /// Replaces the structure's processors. Add [com.ametrin.structures.structure.InlineFromStructureProcessor#INSTANCE] to keep them.
    public B processors(Holder<StructureProcessorList> processors) {
        this.processors = processors;
        return self();
    }

    /// Replaces the structure's processors. Add [com.ametrin.structures.structure.InlineFromStructureProcessor#INSTANCE] to keep them.
    /// Stored inline.
    public B processors(List<StructureProcessor> processors) {
        return processors(Holder.direct(new StructureProcessorList(processors)));
    }

    /// Defaults to the structure's liquid settings.
    public B liquidSettings(LiquidSettings liquidSettings) {
        this.liquidSettings = Optional.of(liquidSettings);
        return self();
    }

    /// Leaves foam in place and places the air the template saved, like a vanilla element.
    public B noFoamProcessing() {
        this.processFoam = false;
        return self();
    }

    /// Extends the bottom of the piece down to the ground, repeating each bottom block. Replaces the structure's foundation.
    public B foundation() {
        return foundation(Foundation.repeating());
    }

    /// Extends the bottom of the piece down to the ground with `state`, at most `maxDepth` blocks. Replaces the structure's foundation.
    public B foundation(BlockState state, int maxDepth) {
        return foundation(Foundation.of(state, maxDepth));
    }

    /// Replaces the structure's foundation.
    public B foundation(Foundation foundation) {
        this.foundation = Optional.of(foundation);
        return self();
    }

    /// The element for `template` with the settings as they are now.
    protected Function<StructureTemplatePool.Projection, ExtendedSinglePoolElement> element(Identifier template) {
        var processors = this.processors != null ? this.processors : ExtendedSinglePoolElement.NO_PROCESSORS;
        var liquidSettings = this.liquidSettings;
        var processFoam = this.processFoam;
        var foundation = this.foundation;
        return projection -> new ExtendedSinglePoolElement(Either.left(template), processors, projection, liquidSettings, processFoam, foundation);
    }

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }
}
