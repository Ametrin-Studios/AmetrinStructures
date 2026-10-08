package com.ametrin.structures.foam;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/// The default tooltip lines of spread behaviors and restrictions: a translation keyed by the
/// type's registry id, falling back to the id itself so a missing lang entry still says something.
final class FoamDescriptions {
    private FoamDescriptions() {}

    static MutableComponent of(String prefix, @Nullable Identifier type, Object... arguments) {
        if (type == null) {
            return Component.literal("unregistered " + prefix);
        }
        return Component.translatableWithFallback(Util.makeDescriptionId(prefix, type), type.toString(), arguments);
    }
}
