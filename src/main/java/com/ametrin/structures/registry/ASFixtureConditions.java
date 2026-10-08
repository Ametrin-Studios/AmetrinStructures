package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.FixtureConditionType;
import com.ametrin.structures.fixture.FixtureConditions;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASFixtureConditions {
    public static final DeferredRegister<FixtureConditionType> REGISTER = DeferredRegister.create(ASRegistries.FIXTURE_CONDITION_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<FixtureConditionType, FixtureConditionType> BIOME = REGISTER.register("biome", () -> new FixtureConditionType(FixtureConditions.InBiome.CODEC));
    public static final DeferredHolder<FixtureConditionType, FixtureConditionType> DIMENSION = REGISTER.register("dimension", () -> new FixtureConditionType(FixtureConditions.InDimension.CODEC));
    public static final DeferredHolder<FixtureConditionType, FixtureConditionType> HEIGHT_RANGE = REGISTER.register("height_range", () -> new FixtureConditionType(FixtureConditions.HeightRange.CODEC));
    public static final DeferredHolder<FixtureConditionType, FixtureConditionType> NOT = REGISTER.register("not", () -> new FixtureConditionType(FixtureConditions.Not.CODEC));
    public static final DeferredHolder<FixtureConditionType, FixtureConditionType> ANY_OF = REGISTER.register("any_of", () -> new FixtureConditionType(FixtureConditions.AnyOf.CODEC));
    public static final DeferredHolder<FixtureConditionType, FixtureConditionType> ALL_OF = REGISTER.register("all_of", () -> new FixtureConditionType(FixtureConditions.AllOf.CODEC));

    private ASFixtureConditions() {}
}
