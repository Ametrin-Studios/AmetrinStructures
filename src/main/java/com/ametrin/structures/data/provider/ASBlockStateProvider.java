package com.ametrin.structures.data.provider;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.FixtureBlock;
import com.ametrin.structures.registry.ASBlocks;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ASBlockStateProvider extends BlockStateProvider {
    public ASBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AmetrinStructures.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        var foam = cubeAll(ASBlocks.FOAM.get());
        simpleBlockWithItem(ASBlocks.FOAM.get(), foam);
        simpleBlock(ASBlocks.FOAM_SPREADER.get(), foam);
        fixture();
    }

    private void fixture() {
        var block = ASBlocks.FIXTURE.get();
        var front = blockTexture(Blocks.STRUCTURE_BLOCK);
        var side = blockTexture(Blocks.JIGSAW).withSuffix("_side");
        var model = models().withExistingParent(name(block), mcLoc("block/cube_directional"))
                .texture("particle", front)
                .texture("north", front)
                .texture("south", front)
                .texture("up", side)
                .texture("down", side)
                .texture("west", side)
                .texture("east", side);
        getVariantBuilder(block).forAllStatesExcept(state -> rotated(model, state.getValue(FixtureBlock.ORIENTATION)), FixtureBlock.WATERLOGGED);
        simpleBlockItem(block, model);
    }

    // Like vanilla's jigsaw block.
    private static ConfiguredModel[] rotated(ModelFile model, FrontAndTop orientation) {
        var builder = ConfiguredModel.builder().modelFile(model);
        switch (orientation) {
            case DOWN_NORTH -> builder.rotationX(90);
            case DOWN_SOUTH -> builder.rotationX(90).rotationY(180);
            case DOWN_WEST -> builder.rotationX(90).rotationY(270);
            case DOWN_EAST -> builder.rotationX(90).rotationY(90);
            case UP_NORTH -> builder.rotationX(270).rotationY(180);
            case UP_SOUTH -> builder.rotationX(270);
            case UP_WEST -> builder.rotationX(270).rotationY(90);
            case UP_EAST -> builder.rotationX(270).rotationY(270);
            case NORTH_UP -> {}
            case SOUTH_UP -> builder.rotationY(180);
            case WEST_UP -> builder.rotationY(270);
            case EAST_UP -> builder.rotationY(90);
        }
        return builder.build();
    }

    private static String name(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }
}
