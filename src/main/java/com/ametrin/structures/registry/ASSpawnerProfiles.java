package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.spawner.SpawnerProfile;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;

public final class ASSpawnerProfiles {
    public static final ResourceKey<SpawnerProfile> DEFAULT = key("default");

    private ASSpawnerProfiles() {}

    public static void bootstrap(BootstrapContext<SpawnerProfile> context) {
        context.register(DEFAULT, SpawnerProfile.DEFAULT);
    }

    private static ResourceKey<SpawnerProfile> key(String name) {
        return ResourceKey.create(ASRegistries.SPAWNER_PROFILE, AmetrinStructures.locate(name));
    }
}
