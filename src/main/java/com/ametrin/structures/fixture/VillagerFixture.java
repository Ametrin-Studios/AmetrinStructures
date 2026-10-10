package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// A villager or zombie villager with the given type and profession, or random ones. A villager with
/// a profession keeps it without a workstation, as if it had traded once.
public record VillagerFixture(
        Optional<ResourceKey<VillagerType>> villagerType,
        Optional<ResourceKey<VillagerProfession>> profession,
        boolean zombie) implements Fixture {
    public static final FixtureField<Optional<ResourceKey<VillagerType>>> TYPE = FixtureField.optional("villager_type", FieldType.registryKey(Registries.VILLAGER_TYPE));
    public static final FixtureField<Optional<ResourceKey<VillagerProfession>>> PROFESSION = FixtureField.optional("profession", FieldType.registryKey(Registries.VILLAGER_PROFESSION));
    public static final FixtureField<Boolean> ZOMBIE = FixtureField.withDefault("zombie", FieldType.bool(), false);
    public static final List<FixtureField<?>> FIELDS = List.of(TYPE, PROFESSION, ZOMBIE);
    public static final MapCodec<VillagerFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    TYPE.forGetter(VillagerFixture::villagerType),
                    PROFESSION.forGetter(VillagerFixture::profession),
                    ZOMBIE.forGetter(VillagerFixture::zombie))
            .apply(instance, VillagerFixture::new));

    @Override
    public void apply(FixtureContext context) {
        Fixtures.spawnEntity(zombie ? EntityTypes.ZOMBIE_VILLAGER : EntityTypes.VILLAGER, null, context, entity -> {
            if (!(entity instanceof VillagerDataHolder villager)) {
                return;
            }
            var registries = context.level().registryAccess();
            var data = villager.getVillagerData();
            if (villagerType.isPresent()) {
                data = data.withType(registries, villagerType.get());
            }
            if (profession.isPresent()) {
                data = data.withProfession(registries, profession.get());
            }
            villager.setVillagerData(data);
            // A villager without experience or a workstation loses its profession within seconds.
            if (profession.isPresent()) {
                switch (entity) {
                    case Villager adult when adult.getVillagerXp() == 0 -> adult.setVillagerXp(1);
                    case ZombieVillager zombie when zombie.getVillagerXp() == 0 -> zombie.setVillagerXp(1);
                    default -> {}
                }
            }
        });
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Fixtures.present(villagerType, profession);
    }

    @Override
    public FixtureType type() {
        return ASFixtures.VILLAGER.get();
    }
}
