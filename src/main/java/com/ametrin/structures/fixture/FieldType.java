package com.ametrin.structures.fixture;

import com.ametrin.structures.util.ASCodecs;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/// A kind of value a [FixtureField] holds: how it's stored, and how the authoring screen parses,
/// shows and completes it.
///
/// @param name        shown in the authoring screen, such as `block state`
/// @param codec       how the value is stored
/// @param parser      reads the text typed into the authoring screen
/// @param formatter   writes a value as that text
/// @param editor      the widget that edits it
/// @param suggestions completion candidates, or the choices of a [Editor#CHOICE]
/// @param registry    the registry a key points into, so the screen can ask the server for its keys
/// @param example     placeholder text
public record FieldType<T>(
        String name,
        Codec<T> codec,
        Parser<T> parser,
        Formatter<T> formatter,
        Editor editor,
        Function<HolderLookup.Provider, List<String>> suggestions,
        Optional<ResourceKey<? extends Registry<?>>> registry,
        Optional<String> example) {

    public enum Editor {
        TEXT,
        BLOCK_STATE,
        TOGGLE,
        CHOICE
    }

    @FunctionalInterface
    public interface Parser<T> {
        DataResult<T> parse(String text, HolderLookup.Provider registries);
    }

    @FunctionalInterface
    public interface Formatter<T> {
        String format(T value, HolderLookup.Provider registries);
    }

    public static FieldType<Boolean> bool() {
        return new FieldType<>("boolean", Codec.BOOL, (text, _) -> switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> DataResult.success(true);
            case "false" -> DataResult.success(false);
            default -> DataResult.error(() -> "not true or false: " + text);
        }, (value, _) -> value.toString(), Editor.TOGGLE, _ -> List.of("true", "false"), Optional.empty(), Optional.empty());
    }

    public static FieldType<Integer> integer(int min, int max) {
        return new FieldType<>("integer", Codec.intRange(min, max), (text, _) -> {
            try {
                return inRange(Integer.parseInt(text.trim()), min, max);
            } catch (NumberFormatException exception) {
                return DataResult.error(() -> "not a whole number: " + text);
            }
        }, (value, _) -> value.toString(), Editor.TEXT, _ -> List.of(), Optional.empty(), Optional.of(Integer.toString(Math.max(min, 0))));
    }

    public static FieldType<Float> number(float min, float max) {
        return new FieldType<>("number", Codec.floatRange(min, max), (text, _) -> {
            try {
                return inRange(Float.parseFloat(text.trim()), min, max);
            } catch (NumberFormatException exception) {
                return DataResult.error(() -> "not a number: " + text);
            }
        }, (value, _) -> value.toString(), Editor.TEXT, _ -> List.of(), Optional.empty(), Optional.of(Float.toString(max)));
    }

    /// An entry of `registry`, which may not exist yet: it's only looked up when the fixture runs.
    public static <R> FieldType<ResourceKey<R>> registryKey(ResourceKey<? extends Registry<R>> registry) {
        return new FieldType<>(
                registry.identifier().getPath(),
                ResourceKey.codec(registry),
                (text, _) -> Identifier.read(text.trim()).map(id -> ResourceKey.create(registry, id)),
                (value, _) -> value.identifier().toString(),
                Editor.TEXT,
                registries -> registries.lookup(registry)
                        .map(lookup -> lookup.listElementIds().map(key -> key.identifier().toString()).toList())
                        .orElseGet(List::of),
                Optional.of(registry),
                Optional.empty());
    }

    /// Written like `/setblock` does: `minecraft:chest[facing=north]`.
    public static FieldType<BlockState> blockState() {
        return new FieldType<>("block state", ASCodecs.BLOCK_STATE, (text, registries) -> {
            try {
                return DataResult.success(BlockStateParser.parseForBlock(registries.lookupOrThrow(Registries.BLOCK), text.trim(), false).blockState());
            } catch (CommandSyntaxException exception) {
                return DataResult.error(exception::getMessage);
            }
        }, (value, _) -> BlockStateParser.serialize(value), Editor.BLOCK_STATE, _ -> List.of(), Optional.empty(), Optional.of("minecraft:chest[facing=north]"));
    }

    /// Written like `/give` does, with an optional count: `minecraft:leather_helmet[dyed_color=255] 2`.
    public static FieldType<ItemStackTemplate> item() {
        return new FieldType<>("item", ItemStackTemplate.CODEC, FieldType::parseItem, FieldType::formatItem, Editor.TEXT,
                _ -> BuiltInRegistries.ITEM.keySet().stream().map(Identifier::toString).toList(),
                Optional.empty(), Optional.of("minecraft:diamond 3"));
    }

    /// SNBT, like `{Health:5f}`.
    public static FieldType<CompoundTag> nbt() {
        return new FieldType<>("nbt", TagParser.LENIENT_CODEC, (text, _) -> {
            try {
                return DataResult.success(TagParser.parseCompoundFully(text.trim()));
            } catch (CommandSyntaxException exception) {
                return DataResult.error(exception::getMessage);
            }
        }, (value, _) -> value.toString(), Editor.TEXT, _ -> List.of(), Optional.empty(), Optional.of("{Key:1}"));
    }

    public static <E extends Enum<E> & StringRepresentable> FieldType<E> choice(Class<E> type) {
        var constants = type.getEnumConstants();
        return new FieldType<>(
                type.getSimpleName(),
                StringRepresentable.fromValues(() -> constants),
                (text, _) -> Arrays.stream(constants)
                        .filter(constant -> constant.getSerializedName().equalsIgnoreCase(text.trim()))
                        .findFirst()
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "not a " + type.getSimpleName() + ": " + text)),
                (value, _) -> value.getSerializedName(),
                Editor.CHOICE,
                _ -> Arrays.stream(constants).map(StringRepresentable::getSerializedName).toList(),
                Optional.empty(),
                Optional.empty());
    }

    /// Also rejects values `valid` refuses, when stored and when typed.
    public FieldType<T> validated(Predicate<T> valid, String requirement) {
        Function<T, DataResult<T>> check = value -> valid.test(value) ? DataResult.success(value) : DataResult.error(() -> requirement);
        return new FieldType<>(name, codec.validate(check), (text, registries) -> parser.parse(text, registries).flatMap(check),
                formatter, editor, suggestions, registry, example);
    }

    /// Shows values as `formatter` writes them. The text must parse back to a value that works the same.
    public FieldType<T> formattedBy(Formatter<T> formatter) {
        return new FieldType<>(name, codec, parser, formatter, editor, suggestions, registry, example);
    }

    /// Parses `text` and stores it as NBT.
    public DataResult<Tag> textToTag(String text, HolderLookup.Provider registries) {
        return parser.parse(text, registries).flatMap(value -> codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value));
    }

    /// Reads a stored value back as the text that parses to it.
    public DataResult<String> tagToText(Tag tag, HolderLookup.Provider registries) {
        return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).map(value -> formatter.format(value, registries));
    }

    public String format(T value, HolderLookup.Provider registries) {
        return formatter.format(value, registries);
    }

    private static <N extends Comparable<N>> DataResult<N> inRange(N value, N min, N max) {
        return value.compareTo(min) < 0 || value.compareTo(max) > 0
                ? DataResult.error(() -> value + " is outside " + min + " to " + max)
                : DataResult.success(value);
    }

    private static DataResult<ItemStackTemplate> parseItem(String text, HolderLookup.Provider registries) {
        var reader = new StringReader(text.trim());
        try {
            var input = new ItemParser(registries).parse(reader);
            reader.skipWhitespace();
            int count = reader.canRead() ? reader.readInt() : 1;
            reader.skipWhitespace();
            if (reader.canRead()) {
                return DataResult.error(() -> "unexpected text after the count: " + reader.getRemaining());
            }
            return count < 1
                    ? DataResult.error(() -> "count " + count + " is below 1")
                    : DataResult.success(new ItemStackTemplate(input.item(), count, input.components()));
        } catch (CommandSyntaxException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    // The item's id, its components the way ItemParser reads them, and the count unless it's 1.
    private static String formatItem(ItemStackTemplate item, HolderLookup.Provider registries) {
        var text = new StringBuilder(item.typeHolder().getRegisteredName());
        var components = new ArrayList<String>();
        DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var patch = item.components();
        for (var type : patch.keySet()) {
            var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            var patched = patch.getPatch(type);
            if (patched == null) {
                components.add("!" + id);
            } else {
                encodeComponent(type, patched, ops).ifPresent(value -> components.add(id + "=" + value));
            }
        }
        if (!components.isEmpty()) {
            text.append('[').append(String.join(",", components)).append(']');
        }
        if (item.count() != 1) {
            text.append(' ').append(item.count());
        }
        return text.toString();
    }

    @SuppressWarnings("unchecked")
    private static <C> Optional<String> encodeComponent(DataComponentType<C> type, Object value, DynamicOps<Tag> ops) {
        var codec = type.codec();
        return codec == null ? Optional.empty() : codec.encodeStart(ops, (C) value).result().map(Tag::toString);
    }
}
