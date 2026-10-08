package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.foam.FoamBlockItem;
import com.ametrin.structures.foam.FoamPresets;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(AmetrinStructures.MOD_ID);

    public static final DeferredItem<FoamBlockItem> FOAM = REGISTER.registerItem("foam",
            properties -> new FoamBlockItem(ASBlocks.FOAM.get(), properties),
            properties -> properties.useBlockDescriptionPrefix().component(ASDataComponents.FOAM_SPREAD, FoamPresets.BASIC));

    public static final DeferredItem<BlockItem> FIXTURE = REGISTER.registerSimpleBlockItem(ASBlocks.FIXTURE);
}
