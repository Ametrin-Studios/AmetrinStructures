package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.FixtureConditionType;
import com.ametrin.structures.fixture.FixturePreset;
import com.ametrin.structures.fixture.FixtureType;
import com.ametrin.structures.foam.FoamSpreadBehaviorType;
import com.ametrin.structures.foam.FoamSpreadRestrictionType;
import com.ametrin.structures.spawner.SpawnerProfile;
import com.ametrin.structures.structure.filter.PlacementFilterType;
import com.ametrin.structures.structure.simple.PieceSourceType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

public final class ASRegistries {
    public static final ResourceKey<Registry<FixturePreset>> FIXTURE_PRESET = key("fixture_preset");
    public static final ResourceKey<Registry<FoamSpreadBehaviorType>> FOAM_SPREAD_BEHAVIOR_TYPE = key("foam_spread_behavior");
    public static final ResourceKey<Registry<FoamSpreadRestrictionType>> FOAM_SPREAD_RESTRICTION_TYPE = key("foam_spread_restriction");
    public static final ResourceKey<Registry<FixtureType>> FIXTURE_TYPE = key("fixture_type");
    public static final ResourceKey<Registry<FixtureConditionType>> FIXTURE_CONDITION_TYPE = key("fixture_condition");
    public static final ResourceKey<Registry<SpawnerProfile>> SPAWNER_PROFILE = key("spawner_profile");
    public static final ResourceKey<Registry<PieceSourceType>> PIECE_SOURCE_TYPE = key("piece_source");
    public static final ResourceKey<Registry<PlacementFilterType>> PLACEMENT_FILTER_TYPE = key("placement_filter");

    public static final Registry<FoamSpreadBehaviorType> FOAM_SPREAD_BEHAVIOR_TYPES = simple(FOAM_SPREAD_BEHAVIOR_TYPE).defaultKey(AmetrinStructures.locate("faces")).create();
    public static final Registry<FoamSpreadRestrictionType> FOAM_SPREAD_RESTRICTION_TYPES = simple(FOAM_SPREAD_RESTRICTION_TYPE).create();
    public static final Registry<FixtureType> FIXTURE_TYPES = simple(FIXTURE_TYPE).create();
    public static final Registry<FixtureConditionType> FIXTURE_CONDITION_TYPES = simple(FIXTURE_CONDITION_TYPE).create();
    public static final Registry<PieceSourceType> PIECE_SOURCE_TYPES = simple(PIECE_SOURCE_TYPE).create();
    public static final Registry<PlacementFilterType> PLACEMENT_FILTER_TYPES = simple(PLACEMENT_FILTER_TYPE).create();

    private ASRegistries() {}

    public static void register(NewRegistryEvent event) {
        event.register(FOAM_SPREAD_BEHAVIOR_TYPES);
        event.register(FOAM_SPREAD_RESTRICTION_TYPES);
        event.register(FIXTURE_TYPES);
        event.register(FIXTURE_CONDITION_TYPES);
        event.register(PIECE_SOURCE_TYPES);
        event.register(PLACEMENT_FILTER_TYPES);
    }

    public static void registerDatapackRegistries(NewDatapackRegistryEvent event) {
        event.worldRegistry(FIXTURE_PRESET, FixturePreset.CODEC);
        event.worldRegistry(SPAWNER_PROFILE, SpawnerProfile.CODEC);
    }

    private static <T> ResourceKey<Registry<T>> key(String name) {
        return ResourceKey.createRegistryKey(AmetrinStructures.locate(name));
    }

    private static <T> RegistryBuilder<T> simple(ResourceKey<Registry<T>> key) {
        return new RegistryBuilder<>(key).sync(false);
    }
}
