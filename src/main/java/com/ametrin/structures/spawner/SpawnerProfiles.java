package com.ametrin.structures.spawner;

import com.ametrin.structures.registry.ASAttachments;
import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.util.ASLog;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BaseSpawner;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

@ApiStatus.Internal
public final class SpawnerProfiles {
    private SpawnerProfiles() {}

    public static void applyAttached(IAttachmentHolder holder, BaseSpawner spawner, HolderLookup.Provider registries) {
        holder.getExistingData(ASAttachments.SPAWNER_PROFILE)
                .flatMap(attachment -> resolve(attachment, registries))
                .ifPresent(profile -> SpawnerAccess.apply(spawner, profile));
    }

    /// Empty when the profile is missing, which leaves the spawner as it is.
    public static Optional<SpawnerProfile> resolve(SpawnerProfileAttachment attachment, HolderLookup.Provider registries) {
        var profiles = registries.lookup(ASRegistries.SPAWNER_PROFILE);
        // Clients don't have the registry: the server applies the profile and sends them the result.
        if (profiles.isEmpty()) {
            return Optional.empty();
        }
        var key = ResourceKey.create(ASRegistries.SPAWNER_PROFILE, attachment.id());
        var profile = profiles.get().get(key).map(Holder::value);
        if (profile.isEmpty()) {
            ASLog.warn("unknown spawner profile {}", attachment.id());
        }
        return profile;
    }
}
