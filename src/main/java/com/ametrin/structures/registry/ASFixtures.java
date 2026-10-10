package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.FixtureType;
import com.ametrin.structures.fixture.ArchaeologyFixture;
import com.ametrin.structures.fixture.BlockStateFixture;
import com.ametrin.structures.fixture.EmptyFixture;
import com.ametrin.structures.fixture.EntityFixture;
import com.ametrin.structures.fixture.ExtendDownFixture;
import com.ametrin.structures.fixture.FeatureFixture;
import com.ametrin.structures.fixture.FillContainerFixture;
import com.ametrin.structures.fixture.LootContainerFixture;
import com.ametrin.structures.fixture.PresetFixture;
import com.ametrin.structures.fixture.SpawnerFixture;
import com.ametrin.structures.fixture.SpawnerProfileFixture;
import com.ametrin.structures.fixture.TrialSpawnerFixture;
import com.ametrin.structures.fixture.VaultFixture;
import com.ametrin.structures.fixture.VillagerFixture;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASFixtures {
    public static final DeferredRegister<FixtureType> REGISTER = DeferredRegister.create(ASRegistries.FIXTURE_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<FixtureType, FixtureType> EMPTY = REGISTER.register("empty", () -> new FixtureType(EmptyFixture.CODEC, EmptyFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> PRESET = REGISTER.register("preset", () -> new FixtureType(PresetFixture.CODEC, PresetFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> LOOT_CONTAINER = REGISTER.register("loot_container", () -> new FixtureType(LootContainerFixture.CODEC, LootContainerFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> FILL_CONTAINER = REGISTER.register("fill_container", () -> new FixtureType(FillContainerFixture.CODEC, FillContainerFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> ENTITY = REGISTER.register("entity", () -> new FixtureType(EntityFixture.CODEC, EntityFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> VILLAGER = REGISTER.register("villager", () -> new FixtureType(VillagerFixture.CODEC, VillagerFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> SPAWNER = REGISTER.register("spawner", () -> new FixtureType(SpawnerFixture.CODEC, SpawnerFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> SPAWNER_PROFILE = REGISTER.register("spawner_profile", () -> new FixtureType(SpawnerProfileFixture.CODEC, SpawnerProfileFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> TRIAL_SPAWNER = REGISTER.register("trial_spawner", () -> new FixtureType(TrialSpawnerFixture.CODEC, TrialSpawnerFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> VAULT = REGISTER.register("vault", () -> new FixtureType(VaultFixture.CODEC, VaultFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> BLOCK_STATE = REGISTER.register("block_state", () -> new FixtureType(BlockStateFixture.CODEC, BlockStateFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> FEATURE = REGISTER.register("feature", () -> new FixtureType(FeatureFixture.CODEC, FeatureFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> ARCHAEOLOGY = REGISTER.register("archaeology", () -> new FixtureType(ArchaeologyFixture.CODEC, ArchaeologyFixture.FIELDS));
    public static final DeferredHolder<FixtureType, FixtureType> EXTEND_DOWN = REGISTER.register("extend_down", () -> new FixtureType(ExtendDownFixture.CODEC, ExtendDownFixture.FIELDS));
}
