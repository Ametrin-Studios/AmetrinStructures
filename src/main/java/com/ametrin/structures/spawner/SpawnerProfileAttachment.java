package com.ametrin.structures.spawner;

import com.ametrin.structures.AmetrinStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

public record SpawnerProfileAttachment(Identifier id) {
    public static final SpawnerProfileAttachment DEFAULT = new SpawnerProfileAttachment(AmetrinStructures.locate("default"));

    public static final MapCodec<SpawnerProfileAttachment> CODEC = Identifier.CODEC.fieldOf("id").xmap(SpawnerProfileAttachment::new, SpawnerProfileAttachment::id);
}
