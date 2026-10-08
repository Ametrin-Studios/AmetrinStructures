package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.FixtureBlock;
import com.ametrin.structures.foam.FoamBlock;
import com.ametrin.structures.foam.FoamSpreaderBlock;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ASBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(AmetrinStructures.MOD_ID);

    static {
        for (var gel : List.of("blue_gel", "orange_gel", "cyan_gel", "yellow_gel", "red_gel")) {
            REGISTER.addAlias(Identifier.fromNamespaceAndPath("structure_gel", gel), AmetrinStructures.locate("foam"));
        }
        REGISTER.addAlias(Identifier.fromNamespaceAndPath("structure_gel", "data_handler"), AmetrinStructures.locate("fixture"));
    }

    public static final DeferredBlock<FoamBlock> FOAM = REGISTER.registerBlock("foam", FoamBlock::new, ASBlocks::foamProperties);

    public static final DeferredBlock<FoamSpreaderBlock> FOAM_SPREADER = REGISTER.registerBlock("foam_spreader", FoamSpreaderBlock::new, ASBlocks::foamProperties);

    public static final DeferredBlock<FixtureBlock> FIXTURE = REGISTER.registerBlock(
            "fixture",
            FixtureBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .dynamicShape() // The support shape follows the block entity's becomes state, so it can't be cached per state.
                    .pushReaction(PushReaction.IMMOVEABLE));

    private static BlockBehaviour.Properties foamProperties(BlockBehaviour.Properties properties) {
        return properties
                .mapColor(MapColor.NONE)
                .pushReaction(PushReaction.POPPED)
                .noCollision()
                .strength(0.0F)
                .noLootTable()
                .noOcclusion()
                .sound(SoundType.BAMBOO)
                .isValidSpawn((_, _, _, _) -> false)
                .isSuffocating((_, _, _) -> false)
                .isViewBlocking((_, _, _, _) -> false)
                .isRedstoneConductor((_, _, _) -> false);
    }
}
