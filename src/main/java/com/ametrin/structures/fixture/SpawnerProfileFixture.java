package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASAttachments;
import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.spawner.SpawnerAccess;
import com.ametrin.structures.spawner.SpawnerProfile;
import com.ametrin.structures.spawner.SpawnerProfileAttachment;
import com.ametrin.structures.spawner.SpawnerProfiles;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.stream.Stream;

/// A spawner that takes its settings from a [SpawnerProfile] every time it loads.
public record SpawnerProfileFixture(ResourceKey<SpawnerProfile> profile, boolean minecart) implements Fixture {
    public static final FixtureField<ResourceKey<SpawnerProfile>> PROFILE = FixtureField.required("profile", FieldType.registryKey(ASRegistries.SPAWNER_PROFILE));
    public static final FixtureField<Boolean> MINECART = FixtureField.withDefault("minecart", FieldType.bool(), false);
    public static final List<FixtureField<?>> FIELDS = List.of(PROFILE, MINECART);
    public static final MapCodec<SpawnerProfileFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    PROFILE.forGetter(SpawnerProfileFixture::profile),
                    MINECART.forGetter(SpawnerProfileFixture::minecart))
            .apply(instance, SpawnerProfileFixture::new));

    @Override
    public void apply(FixtureContext context) {
        var attachment = new SpawnerProfileAttachment(profile.identifier());
        Fixtures.placeSpawner(context, minecart, (holder, spawner) -> {
            holder.setData(ASAttachments.SPAWNER_PROFILE, attachment);
            // Applied now as well as on every later load: a freshly generated block entity is not reloaded.
            SpawnerProfiles.resolve(attachment, context.level().registryAccess())
                    .ifPresent(resolved -> SpawnerAccess.applyWithSpawnDelay(spawner, resolved));
        });
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Stream.of(profile);
    }

    @Override
    public FixtureType type() {
        return ASFixtures.SPAWNER_PROFILE.get();
    }
}
