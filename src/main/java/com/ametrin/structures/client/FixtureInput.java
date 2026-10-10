package com.ametrin.structures.client;

import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.fixture.FixtureCondition;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/// Parses the fixture screen's text, empty when invalid.
final class FixtureInput {
    private FixtureInput() {}

    static Optional<BlockState> parseState(HolderLookup.Provider registries, String raw) {
        if (raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(BlockStateParser.parseForBlock(registries.lookupOrThrow(Registries.BLOCK), raw, false).blockState());
        } catch (CommandSyntaxException exception) {
            return Optional.empty();
        }
    }

    static Optional<Double> parseOffset(String raw) {
        try {
            var value = Double.parseDouble(raw.trim());
            return FixtureBlockEntity.isValidOffset(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    // Whole numbers without the trailing `.0`.
    static String formatOffset(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    static Optional<Integer> parseWeight(String raw) {
        try {
            var weight = Integer.parseInt(raw.trim());
            return weight >= 1 ? Optional.of(weight) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    static Optional<Float> parseChance(String raw) {
        try {
            var chance = Float.parseFloat(raw.trim());
            return chance >= 0.0F && chance <= 1.0F ? Optional.of(chance) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    static Optional<Tag> parseConditions(HolderLookup.Provider registries, String raw) {
        try {
            var tag = TagParser.create(NbtOps.INSTANCE).parseFully(raw.trim());
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            return FixtureCondition.LIST_CODEC.parse(ops, tag).isSuccess() ? Optional.of(tag) : Optional.empty();
        } catch (CommandSyntaxException exception) {
            return Optional.empty();
        }
    }
}
