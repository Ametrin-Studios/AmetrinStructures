package com.ametrin.structures.spawner;

import com.ametrin.structures.AmetrinStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

public record SpawnerProfileAttachment(ResourceLocation id) {
    public static final SpawnerProfileAttachment DEFAULT = new SpawnerProfileAttachment(AmetrinStructures.locate("default"));

    public static final MapCodec<SpawnerProfileAttachment> CODEC = ResourceLocation.CODEC.fieldOf("id").xmap(SpawnerProfileAttachment::new, SpawnerProfileAttachment::id);
}
