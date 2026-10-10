package com.ametrin.structures.fixture;

import com.ametrin.structures.util.ASLog;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public record WeightedFixture(int weight, float generationChance, List<FixtureCondition> conditions, Fixture fixture) {
    public static final String WEIGHT_KEY = "weight";
    public static final String GENERATION_CHANCE_KEY = "generation_chance";
    public static final String CONDITIONS_KEY = "conditions";

    public static final MapCodec<WeightedFixture> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf(WEIGHT_KEY, 1).forGetter(WeightedFixture::weight),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf(GENERATION_CHANCE_KEY, 1.0F).forGetter(WeightedFixture::generationChance),
                    FixtureCondition.LIST_CODEC.optionalFieldOf(CONDITIONS_KEY, List.of()).forGetter(WeightedFixture::conditions),
                    Fixture.MAP_CODEC.forGetter(WeightedFixture::fixture))
            .apply(instance, WeightedFixture::new));

    public static final Codec<List<WeightedFixture>> LIST_CODEC = new LenientListCodec();

    public WeightedFixture {
        conditions = List.copyOf(conditions);
    }

    public WeightedFixture(int weight, float generationChance, Fixture fixture) {
        this(weight, generationChance, List.of(), fixture);
    }

    public WeightedFixture(int weight, Fixture fixture) {
        this(weight, 1.0F, fixture);
    }

    /// Draws one of the alternatives `eligible` accepts, by weight. Empty when there are none.
    public static Optional<WeightedFixture> draw(List<WeightedFixture> alternatives, Predicate<WeightedFixture> eligible, RandomSource random) {
        var candidates = alternatives.stream().filter(eligible).toList();
        int total = candidates.stream().mapToInt(WeightedFixture::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (var alternative : candidates) {
            roll -= alternative.weight();
            if (roll < 0) {
                return Optional.of(alternative);
            }
        }
        return Optional.of(candidates.getLast());
    }

    /// A condition that throws, e.g. by looking outside the generating area, counts as failed.
    public boolean conditionsPass(FixtureCondition.Context context) {
        for (var condition : conditions) {
            try {
                if (!condition.test(context)) {
                    return false;
                }
            } catch (RuntimeException exception) {
                ASLog.warn("fixture condition {} at {} failed: {}", condition, context.pos(), exception.toString());
                return false;
            }
        }
        return true;
    }

    /// Decodes one stored alternative, or keeps it as a [Fixture.Unreadable].
    public static <T> WeightedFixture decodeLeniently(DynamicOps<T> ops, T input) {
        var decoded = MAP_CODEC.codec().parse(ops, input);
        if (decoded.result().isPresent()) {
            return decoded.result().get();
        }
        var data = new Dynamic<>(ops, input);
        var error = decoded.error().map(DataResult.Error::message).orElse("");
        return new WeightedFixture(
                Math.max(1, data.get(WEIGHT_KEY).asInt(1)),
                data.get(GENERATION_CHANCE_KEY).asFloat(1.0F),
                data.get(CONDITIONS_KEY).result().flatMap(conditions -> FixtureCondition.LIST_CODEC.parse(conditions).result()).orElseGet(List::of),
                new Fixture.Unreadable(data.convert(NbtOps.INSTANCE).getValue(), error));
    }

    /// Wrap NeoForge's conditions, such as `ModLoadedCondition`, in [FixtureConditions.NeoForge].
    public WeightedFixture withConditions(FixtureCondition... conditions) {
        return new WeightedFixture(weight, generationChance, List.of(conditions), fixture);
    }

    public WeightedFixture withFixture(Fixture fixture) {
        return new WeightedFixture(weight, generationChance, conditions, fixture);
    }

    /// Decodes each alternative separately. One that can't be decoded becomes a [Fixture.Unreadable]
    /// instead of failing the whole list, so a fixture never stops a template from loading.
    private static final class LenientListCodec implements Codec<List<WeightedFixture>> {
        @Override
        public <T> DataResult<Pair<List<WeightedFixture>, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getList(input).map(elements -> {
                var alternatives = new ArrayList<WeightedFixture>();
                elements.accept(element -> alternatives.add(decodeLeniently(ops, element)));
                return Pair.of(List.copyOf(alternatives), input);
            });
        }

        @Override
        public <T> DataResult<T> encode(List<WeightedFixture> input, DynamicOps<T> ops, T prefix) {
            var list = ops.listBuilder();
            for (var alternative : input) {
                list.add(alternative.fixture() instanceof Fixture.Unreadable unreadable
                        ? DataResult.success(new Dynamic<>(NbtOps.INSTANCE, unreadable.data()).convert(ops).getValue())
                        : MAP_CODEC.codec().encodeStart(ops, alternative));
            }
            return list.build(prefix);
        }
    }
}
