package com.ametrin.structures.data.provider;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.registry.ASTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagCopyingItemTagProvider;

import java.util.concurrent.CompletableFuture;

public class ASItemTagsProvider extends BlockTagCopyingItemTagProvider {
    public ASItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockTags) {
        super(output, lookupProvider, blockTags, AmetrinStructures.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        copy(ASTags.Blocks.FOAM, ASTags.Items.FOAM);
        tag(ASTags.Items.FOAM_DISSOLVER).add(Items.AMETHYST_SHARD);
        tag(ASTags.Items.FOAM_INTERACTABLE)
                .addTag(ASTags.Items.FOAM)
                .addTag(ASTags.Items.FOAM_DISSOLVER);
    }
}
