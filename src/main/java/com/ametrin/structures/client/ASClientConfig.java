package com.ametrin.structures.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ASClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue STRUCTURE_BLOCK_NAME_TAGS = BUILDER
            .comment("Show a structure block's template name above it. Turn off if another mod replaces the structure block's renderer.")
            .gameRestart()
            .define("structure_block_name_tags", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ASClientConfig() {}
}
