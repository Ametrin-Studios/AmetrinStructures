package com.ametrin.structures.client;

import com.ametrin.structures.fixture.Fixture;
import com.ametrin.structures.fixture.FixtureField;
import com.ametrin.structures.fixture.WeightedFixture;
import com.ametrin.structures.registry.ASRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/// A fixture alternative while it is edited
/// Text that doesn't parse is kept as it is, so the alternative stays unreadable and is not lost
final class AlternativeDraft {
    String weight = "";
    String chance = "";
    String conditions = "";
    final Map<String, String> texts = new LinkedHashMap<>();
    private Identifier type;
    // Sent back for what the screen doesn't edit, such as an unknown type's fields.
    private CompoundTag stored;

    AlternativeDraft(Identifier type) {
        this.type = type;
        this.stored = typeOnly(type);
    }

    AlternativeDraft(AlternativeDraft source) {
        this.type = source.type;
        this.weight = source.weight;
        this.chance = source.chance;
        this.conditions = source.conditions;
        this.texts.putAll(source.texts);
        this.stored = source.stored.copy();
    }

    AlternativeDraft(Tag stored, HolderLookup.Provider registries) {
        var data = stored instanceof CompoundTag compound ? compound : new CompoundTag();
        this.stored = data;
        this.type = data.getString(Fixture.TYPE_KEY).map(Identifier::tryParse).orElseGet(() -> Identifier.withDefaultNamespace("unknown"));
        this.weight = text(data.get(WeightedFixture.WEIGHT_KEY)).orElse("");
        this.chance = text(data.get(WeightedFixture.GENERATION_CHANCE_KEY)).orElse("");
        this.conditions = Optional.ofNullable(data.get(WeightedFixture.CONDITIONS_KEY)).map(Tag::toString).orElse("");
        for (var field : fields()) {
            var value = data.get(field.key());
            if (value != null) {
                texts.put(field.key(), field.type().tagToText(value, registries).result().or(() -> text(value)).orElse(""));
            }
        }
    }

    Identifier type() {
        return type;
    }

    /// Drops the old type's fields.
    void setType(Identifier type) {
        this.type = type;
        this.texts.clear();
        this.stored = typeOnly(type);
    }

    /// Empty for an unknown type.
    List<FixtureField<?>> fields() {
        var fixture = ASRegistries.FIXTURE_TYPES.getValue(type);
        return fixture == null ? List.of() : fixture.fields();
    }

    Tag toTag(HolderLookup.Provider registries) {
        var data = stored.copy();
        put(data, WeightedFixture.WEIGHT_KEY, weight, value -> FixtureInput.parseWeight(value).map(IntTag::valueOf));
        put(data, WeightedFixture.GENERATION_CHANCE_KEY, chance, value -> FixtureInput.parseChance(value).map(FloatTag::valueOf));
        put(data, WeightedFixture.CONDITIONS_KEY, conditions, value -> FixtureInput.parseConditions(registries, value));
        for (var field : fields()) {
            put(data, field.key(), texts.getOrDefault(field.key(), ""), value -> field.type().textToTag(value, registries).result());
        }
        return data;
    }

    private static CompoundTag typeOnly(Identifier type) {
        var data = new CompoundTag();
        data.putString(Fixture.TYPE_KEY, type.toString());
        return data;
    }

    // Blank text is left out, so it takes its default.
    private static void put(CompoundTag data, String key, String text, Function<String, Optional<? extends Tag>> parse) {
        if (text.isBlank()) {
            data.remove(key);
        } else {
            var parsed = parse.apply(text);
            data.put(key, parsed.isPresent() ? parsed.get() : StringTag.valueOf(text));
        }
    }

    private static Optional<String> text(@Nullable Tag tag) {
        return switch (tag) {
            case null -> Optional.empty();
            case StringTag(String value) -> Optional.of(value);
            case NumericTag number -> Optional.of(number.toString().replaceAll("[bsLfd]$", ""));
            default -> Optional.of(tag.toString());
        };
    }
}
