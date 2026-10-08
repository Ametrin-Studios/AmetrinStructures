package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.foam.FoamSpreaderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoamSpreaderBlockEntity>> FOAM_SPREADER =
            REGISTER.register("foam_spreader", () -> new BlockEntityType<>(FoamSpreaderBlockEntity::new, false, ASBlocks.FOAM_SPREADER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FixtureBlockEntity>> FIXTURE =
            REGISTER.register("fixture", () -> new BlockEntityType<>(FixtureBlockEntity::new, true, ASBlocks.FIXTURE.get()));
}
