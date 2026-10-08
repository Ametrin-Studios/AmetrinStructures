package com.ametrin.structures.data.provider;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.registry.ASTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import java.util.concurrent.CompletableFuture;

public class ASBlockTagsProvider extends BlockTagsProvider {
    public ASBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, AmetrinStructures.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        tag(ASTags.Blocks.FOAM).add(ASBlocks.FOAM.key());
    }
}
