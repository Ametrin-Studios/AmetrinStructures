package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.FixtureType;
import com.ametrin.structures.fixture.Fixtures;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASFixtures {
    public static final DeferredRegister<FixtureType> REGISTER = DeferredRegister.create(ASRegistries.FIXTURE_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<FixtureType, FixtureType> EMPTY = REGISTER.register("empty", () -> new FixtureType(Fixtures.Empty.CODEC, Fixtures.Empty.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> PRESET = REGISTER.register("preset", () -> new FixtureType(Fixtures.Preset.CODEC, Fixtures.Preset.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> LOOT_CONTAINER = REGISTER.register("loot_container", () -> new FixtureType(Fixtures.LootContainer.CODEC, Fixtures.LootContainer.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> FILL_CONTAINER = REGISTER.register("fill_container", () -> new FixtureType(Fixtures.FillContainer.CODEC, Fixtures.FillContainer.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> ENTITY = REGISTER.register("entity", () -> new FixtureType(Fixtures.SpawnEntity.CODEC, Fixtures.SpawnEntity.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> VILLAGER = REGISTER.register("villager", () -> new FixtureType(Fixtures.SpawnVillager.CODEC, Fixtures.SpawnVillager.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> SPAWNER = REGISTER.register("spawner", () -> new FixtureType(Fixtures.Spawner.CODEC, Fixtures.Spawner.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> SPAWNER_PROFILE = REGISTER.register("spawner_profile", () -> new FixtureType(Fixtures.ProfileSpawner.CODEC, Fixtures.ProfileSpawner.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> TRIAL_SPAWNER = REGISTER.register("trial_spawner", () -> new FixtureType(Fixtures.TrialSpawner.CODEC, Fixtures.TrialSpawner.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> VAULT = REGISTER.register("vault", () -> new FixtureType(Fixtures.Vault.CODEC, Fixtures.Vault.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> BLOCK_STATE = REGISTER.register("block_state", () -> new FixtureType(Fixtures.PlaceBlockState.CODEC, Fixtures.PlaceBlockState.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> FEATURE = REGISTER.register("feature", () -> new FixtureType(Fixtures.PlaceFeature.CODEC, Fixtures.PlaceFeature.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> ARCHAEOLOGY = REGISTER.register("archaeology", () -> new FixtureType(Fixtures.Archaeology.CODEC, Fixtures.Archaeology.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> EXTEND_DOWN = REGISTER.register("extend_down", () -> new FixtureType(Fixtures.ExtendDown.CODEC, Fixtures.ExtendDown.FIELDS));
}
