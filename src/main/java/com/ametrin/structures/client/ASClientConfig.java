package com.ametrin.structures.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ASClientConfig {
    public static final String STRUCTURE_BLOCK_NAME_TAGS_KEY = "ametrin_structures.configuration.structure_block_name_tags";
    public static final String JIGSAW_NAME_TAGS_KEY = "ametrin_structures.configuration.jigsaw_name_tags";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue STRUCTURE_BLOCK_NAME_TAGS = BUILDER
            .comment("Show a structure block's template name above it. Turn off if another mod replaces the structure block's renderer.")
            .translation(STRUCTURE_BLOCK_NAME_TAGS_KEY)
            .gameRestart()
            .define("structure_block_name_tags", true);

    public static final ModConfigSpec.BooleanValue JIGSAW_NAME_TAGS = BUILDER
            .comment("Show a jigsaw's pool above it. Turn off if another mod adds a renderer for jigsaws.")
            .translation(JIGSAW_NAME_TAGS_KEY)
            .gameRestart()
            .define("jigsaw_name_tags", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ASClientConfig() {}
}
