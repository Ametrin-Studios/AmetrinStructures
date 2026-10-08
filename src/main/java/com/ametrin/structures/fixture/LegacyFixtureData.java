package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASBlockEntities;
import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.util.ASCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;

/// Rewrites the block states of fixture markers saved before Minecraft 26.3 in the current form.
/// Vanilla's data fixers don't know the markers' data.
// TODO 27.1: remove together with the legacy form of ASCodecs#BLOCK_STATE.
@ApiStatus.Internal
public final class LegacyFixtureData {
    private static final String BLOCKS_KEY = "blocks";
    private static final String BLOCK_ENTITY_KEY = "nbt";
    private static final String BLOCK_ENTITY_ID_KEY = "id";
    private static final String LEGACY_NAME_KEY = "Name";

    private LegacyFixtureData() {}

    /// Updates every marker in a saved structure template, in place.
    ///
    /// @return whether anything changed
    public static boolean updateTemplate(CompoundTag template) {
        var markerId = ASBlockEntities.FIXTURE.getId().toString();
        var changed = false;
        for (var block : template.getListOrEmpty(BLOCKS_KEY).compoundStream().toList()) {
            var data = block.getCompoundOrEmpty(BLOCK_ENTITY_KEY);
            if (data.getStringOr(BLOCK_ENTITY_ID_KEY, "").equals(markerId)) {
                changed |= updateMarker(data);
            }
        }
        return changed;
    }

    /// Updates `becomes` and the block state fields of every alternative of a registered type, in
    /// place. Everything else stays as stored.
    ///
    /// @return whether anything changed
    public static boolean updateMarker(CompoundTag marker) {
        var changed = updateBlockState(marker, FixtureBlockEntity.BECOMES_KEY);
        for (var alternative : marker.getListOrEmpty(FixtureBlockEntity.FIXTURES_KEY).compoundStream().toList()) {
            changed |= updateAlternative(alternative);
        }
        return changed;
    }

    private static boolean updateAlternative(CompoundTag alternative) {
        var type = alternative.getString(Fixture.TYPE_KEY).map(Identifier::tryParse).flatMap(ASRegistries.FIXTURE_TYPES::getOptional);
        if (type.isEmpty()) {
            return false;
        }
        var changed = false;
        for (var field : type.get().fields()) {
            if (field.type().editor() == FieldType.Editor.BLOCK_STATE) {
                changed |= updateBlockState(alternative, field.key());
            }
        }
        return changed;
    }

    // Only the old form is rewritten, so a block state written in the current form stays exactly as written.
    private static boolean updateBlockState(CompoundTag data, String key) {
        if (!(data.get(key) instanceof CompoundTag legacy) || !legacy.contains(LEGACY_NAME_KEY)) {
            return false;
        }
        var updated = ASCodecs.BLOCK_STATE.parse(NbtOps.INSTANCE, legacy)
                .flatMap(state -> BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state))
                .result();
        updated.ifPresent(state -> data.put(key, state));
        return updated.isPresent();
    }
}
