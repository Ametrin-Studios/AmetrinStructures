package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.util.ASLog;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner.FullConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// A trial spawner using configs from the `trial_spawner` registry, such as vanilla's
/// `minecraft:trial_chamber/melee/zombie/normal`. Without `ominous_config`, an ominous trial uses
/// `normal_config` as well.
public record TrialSpawnerFixture(
        ResourceKey<TrialSpawnerConfig> normalConfig,
        Optional<ResourceKey<TrialSpawnerConfig>> ominousConfig,
        int targetCooldownLength,
        int requiredPlayerRange) implements Fixture {
    public static final FixtureField<ResourceKey<TrialSpawnerConfig>> NORMAL_CONFIG = FixtureField.required("normal_config", FieldType.registryKey(Registries.TRIAL_SPAWNER_CONFIG));
    public static final FixtureField<Optional<ResourceKey<TrialSpawnerConfig>>> OMINOUS_CONFIG = FixtureField.optional("ominous_config", FieldType.registryKey(Registries.TRIAL_SPAWNER_CONFIG));
    public static final FixtureField<Integer> TARGET_COOLDOWN_LENGTH = FixtureField.withDefault("target_cooldown_length", FieldType.integer(0, Integer.MAX_VALUE), FullConfig.DEFAULT.targetCooldownLength());
    public static final FixtureField<Integer> REQUIRED_PLAYER_RANGE = FixtureField.withDefault("required_player_range", FieldType.integer(1, 128), FullConfig.DEFAULT.requiredPlayerRange());
    public static final List<FixtureField<?>> FIELDS = List.of(NORMAL_CONFIG, OMINOUS_CONFIG, TARGET_COOLDOWN_LENGTH, REQUIRED_PLAYER_RANGE);
    public static final MapCodec<TrialSpawnerFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    NORMAL_CONFIG.forGetter(TrialSpawnerFixture::normalConfig),
                    OMINOUS_CONFIG.forGetter(TrialSpawnerFixture::ominousConfig),
                    TARGET_COOLDOWN_LENGTH.forGetter(TrialSpawnerFixture::targetCooldownLength),
                    REQUIRED_PLAYER_RANGE.forGetter(TrialSpawnerFixture::requiredPlayerRange))
            .apply(instance, TrialSpawnerFixture::new));

    /// A trial spawner with vanilla's cooldown and player range.
    public TrialSpawnerFixture(ResourceKey<TrialSpawnerConfig> normalConfig, ResourceKey<TrialSpawnerConfig> ominousConfig) {
        this(normalConfig, Optional.of(ominousConfig), FullConfig.DEFAULT.targetCooldownLength(), FullConfig.DEFAULT.requiredPlayerRange());
    }

    @Override
    public void apply(FixtureContext context) {
        var registries = context.level().registryAccess();
        var configs = registries.lookupOrThrow(Registries.TRIAL_SPAWNER_CONFIG);
        var normal = config(configs, normalConfig);
        var ominous = ominousConfig.isPresent() ? config(configs, ominousConfig.get()) : normal;
        if (normal.isEmpty() || ominous.isEmpty()) {
            return;
        }
        context.placeBlock(Blocks.TRIAL_SPAWNER.defaultBlockState());
        if (!(context.level().getBlockEntity(context.actionBlockPos()) instanceof TrialSpawnerBlockEntity spawner)) {
            return;
        }
        configure(spawner, new FullConfig(normal.get(), ominous.get(), targetCooldownLength, requiredPlayerRange), registries);
    }

    // The trial spawner has no setter for its config, so load it like a template's block entity data would.
    static void configure(TrialSpawnerBlockEntity spawner, FullConfig config, HolderLookup.Provider registries) {
        var data = FullConfig.MAP_CODEC.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), config).getOrThrow();
        spawner.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, (CompoundTag) data));
    }

    private static Optional<? extends Holder<TrialSpawnerConfig>> config(HolderLookup<TrialSpawnerConfig> configs, ResourceKey<TrialSpawnerConfig> key) {
        var config = configs.get(key);
        if (config.isEmpty()) {
            ASLog.warn("fixture trial spawner config {} is not registered", key.identifier());
        }
        return config;
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Stream.concat(Stream.of(normalConfig), Fixtures.present(ominousConfig));
    }

    @Override
    public FixtureType type() {
        return ASFixtures.TRIAL_SPAWNER.get();
    }
}
