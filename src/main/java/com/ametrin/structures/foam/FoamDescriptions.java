package com.ametrin.structures.foam;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/// The default tooltip lines of spread behaviors and restrictions: a translation keyed by the
/// type's registry id, falling back to the id itself so a missing lang entry still says something.
final class FoamDescriptions {
    private FoamDescriptions() {}

    static MutableComponent of(@Nullable Identifier type, Function<Identifier, String> key, Object... arguments) {
        if (type == null) {
            return Component.literal("unregistered type");
        }
        return Component.translatableWithFallback(key.apply(type), type.toString(), arguments);
    }
}
