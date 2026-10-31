package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.Foundation;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

import java.util.Optional;

/// Gives a piece a copy of its element with the structure's settings, see [StructureSettingsElement].
final class StructureElements {
    private StructureElements() {}

    /// Elements that implement [StructureSettingsElement] decide themselves. Vanilla's single, legacy and list elements take the structure's processors. Other elements stay as they are.
    static StructurePoolElement withStructureSettings(StructurePoolElement element, Optional<Holder<StructureProcessorList>> processors, Optional<Foundation> foundation) {
        if (processors.isEmpty() && foundation.isEmpty()) {
            return element;
        }
        if (element instanceof StructureSettingsElement settings) {
            return settings.withStructureSettings(processors, foundation);
        }
        if (element.getClass() == ListPoolElement.class) {
            var list = (ListPoolElement) element;
            var children = list.getElements().stream().map(child -> withStructureSettings(child, processors, foundation)).toList();
            return children.equals(list.getElements()) ? list : new ListPoolElement(children, list.getProjection());
        }
        if (!(element instanceof SinglePoolElement single)) {
            return element;
        }
        var withProcessors = StructureSettingsElement.processors(single.processors, processors);
        if (withProcessors == single.processors) {
            return element;
        }
        if (element.getClass() == SinglePoolElement.class) {
            return new SinglePoolElement(single.template, withProcessors, single.getProjection(), single.overrideLiquidSettings);
        }
        if (element.getClass() == LegacySinglePoolElement.class) {
            return new LegacySinglePoolElement(single.template, withProcessors, single.getProjection(), single.overrideLiquidSettings);
        }
        return element;
    }
}
