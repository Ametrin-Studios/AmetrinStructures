package com.ametrin.structures.client;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;

@FunctionalInterface
interface Completer {
    /// best first, empty when there are none
    List<Completion> complete(String value);

    static Completer filtering(Supplier<List<String>> candidates) {
        return value -> {
            String needle = value.toLowerCase(Locale.ROOT);
            List<String> prefixed = new ArrayList<>();
            List<String> containing = new ArrayList<>();
            for (String candidate : candidates.get()) {
                String lower = candidate.toLowerCase(Locale.ROOT);
                if (lower.equals(needle)) {
                    return List.of();
                }
                if (lower.startsWith(needle) || lower.substring(lower.indexOf(':') + 1).startsWith(needle)) {
                    prefixed.add(candidate);
                } else if (lower.contains(needle)) {
                    containing.add(candidate);
                }
            }
            prefixed.sort(Comparator.naturalOrder());
            containing.sort(Comparator.naturalOrder());
            prefixed.addAll(containing);
            return prefixed.stream().map(Completion::of).toList();
        };
    }

    static Completer blockState(HolderLookup<Block> blocks, Predicate<String> block) {
        return value -> BlockStateParser.fillSuggestions(blocks, new SuggestionsBuilder(value, 0), false, false)
                .join()
                .getList()
                .stream()
                .map(suggestion -> new Completion(suggestion.getText(), suggestion.apply(value)))
                .filter(completion -> !completion.result().equals(value))
                .filter(completion -> value.contains("[") || block.test(completion.result()))
                .toList();
    }

    record Completion(String label, String result) {
        public static Completion of(String value) {
            return new Completion(value, value);
        }
    }
}
