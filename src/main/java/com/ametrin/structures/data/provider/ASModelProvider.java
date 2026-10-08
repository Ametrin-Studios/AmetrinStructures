package com.ametrin.structures.data.provider;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.registry.ASBlocks;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelLocationUtils;
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
        donateModelTo(blockModels, Blocks.STRUCTURE_BLOCK, ASBlocks.FIXTURE.get());
        blockModels.registerSimpleItemModel(ASBlocks.FIXTURE.get(), ModelLocationUtils.getModelLocation(Blocks.STRUCTURE_BLOCK));
    }

    public static void donateModelTo(BlockModelGenerators blockModels, Block donor, Block copyTo) {
        var donorModelLocation = ModelLocationUtils.getModelLocation(donor);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(copyTo, BlockModelGenerators.plainVariant(donorModelLocation)));
    }

    private static void trivialCube(BlockModelGenerators blockModels, Block block) {
        blockModels.createTrivialCube(block);
        blockModels.registerSimpleItemModel(block, ModelLocationUtils.getModelLocation(block));
    }
}
