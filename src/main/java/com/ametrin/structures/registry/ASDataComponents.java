package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.foam.FoamSpread;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASDataComponents {
    public static final DeferredRegister.DataComponents REGISTER = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FoamSpread>> FOAM_SPREAD = REGISTER.registerComponentType("foam_spread",
            builder -> builder.persistent(FoamSpread.CODEC).networkSynchronized(FoamSpread.STREAM_CODEC).cacheEncoding());

    private ASDataComponents() {}

    /// Puts the foam explanation right under the item name, ahead of any vanilla component lines.
    public static void registerTooltips(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(FOAM_SPREAD, TooltipAppender.createComponentAppender(FOAM_SPREAD.get()));
    }
}
