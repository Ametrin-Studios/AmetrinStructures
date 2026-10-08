package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.util.ASCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

public interface FoamSpreadRestriction {
    Codec<FoamSpreadRestriction> CODEC = ASCodecs.dispatch(
            () -> ASRegistries.FOAM_SPREAD_RESTRICTION_TYPES,
            FoamSpreadRestriction::type,
            FoamSpreadRestrictionType::codec);

    boolean permits(Context context);

    /// Defaults to the translation of `foam_spread_restriction.<namespace>.<path>`. Override to pass parameters.
    default Component description() {
        return FoamDescriptions.of("foam_spread_restriction", ASRegistries.FOAM_SPREAD_RESTRICTION_TYPES.getKey(type()));
    }

    FoamSpreadRestrictionType type();

    /// @param stack empty when the fill was not player-initiated
    record Context(LevelReader level, BlockPos source, ItemStack stack, BlockPos candidate) {
        public Context withCandidate(BlockPos newCandidate) {
            return new Context(level, source, stack, newCandidate);
        }
    }
}
