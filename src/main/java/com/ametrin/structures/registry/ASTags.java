package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

public interface ASTags {

    interface Items {
        TagKey<Item> FOAM = mod("foam");
        TagKey<Item> FOAM_INTERACTABLE = mod("foam_interactable");
        TagKey<Item> FOAM_DISSOLVER = mod("foam_dissolver");

        private static TagKey<Item> mod(String path) {
            return ASItems.REGISTER.createTagKey(path);
        }
    }

    interface Blocks {
        TagKey<Block> FOAM = mod("foam");

        private static TagKey<Block> mod(String path) {
            return ASBlocks.REGISTER.createTagKey(path);
        }
    }

    interface Structures {
        TagKey<Structure> LAKE_PROOF = TagKey.create(Registries.STRUCTURE, AmetrinStructures.locate("lake_proof"));
    }
}
