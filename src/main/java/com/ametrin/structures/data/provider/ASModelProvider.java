package com.ametrin.structures.data.provider;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.FixtureBlock;
import com.ametrin.structures.registry.ASBlocks;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class ASModelProvider extends ModelProvider {
    public ASModelProvider(PackOutput output) {
        super(output, AmetrinStructures.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        trivialCube(blockModels, ASBlocks.FOAM.get());
        donateModelTo(blockModels, ASBlocks.FOAM.get(), ASBlocks.FOAM_SPREADER.get());
        fixture(blockModels);
    }

    public static void donateModelTo(BlockModelGenerators blockModels, Block donor, Block copyTo) {
        var donorModelLocation = ModelLocationUtils.getModelLocation(donor);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(copyTo, BlockModelGenerators.plainVariant(donorModelLocation)));
    }

    private static void trivialCube(BlockModelGenerators blockModels, Block block) {
        blockModels.createTrivialCube(block);
        blockModels.registerSimpleItemModel(block, ModelLocationUtils.getModelLocation(block));
    }

    private static void fixture(BlockModelGenerators blockModels) {
        var block = ASBlocks.FIXTURE.get();
        var frontTexture = TextureMapping.getBlockTexture(Blocks.STRUCTURE_BLOCK);
        var sideTexture = TextureMapping.getBlockTexture(Blocks.JIGSAW, "_side");
        var textures = new TextureMapping()
                .put(TextureSlot.PARTICLE, frontTexture)
                .put(TextureSlot.NORTH, frontTexture)
                .put(TextureSlot.SOUTH, frontTexture)
                .put(TextureSlot.UP, sideTexture)
                .put(TextureSlot.DOWN, sideTexture)
                .put(TextureSlot.WEST, sideTexture)
                .put(TextureSlot.EAST, sideTexture);

        var model = ModelTemplates.CUBE_DIRECTIONAL.create(block, textures, blockModels.modelOutput);

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model))
                .with(PropertyDispatch.modify(FixtureBlock.ORIENTATION).generate(BlockModelGenerators::applyRotation))
        );

        blockModels.registerSimpleItemModel(block, model);
    }
}
