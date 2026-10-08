package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.util.ASCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/// Decides which positions a foam spreader reaches out to.
public interface FoamSpreadBehavior {
    Codec<FoamSpreadBehavior> CODEC = ASCodecs.dispatch(() -> ASRegistries.FOAM_SPREAD_BEHAVIOR_TYPES, FoamSpreadBehavior::type, FoamSpreadBehaviorType::codec);

    /// Candidate positions reachable from [Context#source()], in world space. The caller applies the restrictions and skips occupied positions.
    List<BlockPos> offsets(Context context);

    /// One tooltip line saying where this behavior spreads, e.g. "Spreads to the six adjacent blocks".
    /// Defaults to the translation of `foam_spread_behavior.<namespace>.<path>`, from the type's id; override to pass parameters.
    default Component description() {
        return FoamDescriptions.of("foam_spread_behavior", ASRegistries.FOAM_SPREAD_BEHAVIOR_TYPES.getKey(type()));
    }

    FoamSpreadBehaviorType type();

    /// @param axis present only when a spreader set one
    record Context(BlockPos source, Optional<Direction.Axis> axis) {
        public static Context of(BlockPos source) {
            return new Context(source, Optional.empty());
        }

        public static Context of(BlockPos source, Direction.Axis axis) {
            return new Context(source, Optional.of(axis));
        }
    }
}
