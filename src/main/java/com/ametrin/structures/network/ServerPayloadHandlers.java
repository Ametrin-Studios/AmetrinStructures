package com.ametrin.structures.network;

import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.fixture.FixtureGeneration;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.stream.Stream;

@ApiStatus.Internal
public final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {}

    /// Answers with the keys the server's copy of the registry holds, datapack entries included, or
    /// with the template ids for [ASPayloads.RequestRegistryKeys#STRUCTURE_TEMPLATES]. Only the
    /// authoring screens ask, so only players who may open them get an answer.
    public static void requestRegistryKeys(ASPayloads.RequestRegistryKeys payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.canUseGameMasterBlocks()) {
            return;
        }
        if (payload.registry().equals(ASPayloads.RequestRegistryKeys.STRUCTURE_TEMPLATES)) {
            List<Identifier> templates = player.level().getServer().getStructureTemplateManager().listTemplates().sorted().toList();
            NetworkHelper.sendTo(player, new ASPayloads.SendRegistryKeys(payload.registry(), templates));
            return;
        }
        var key = ResourceKey.createRegistryKey(payload.registry());
        // Loot tables, predicates and item modifiers are not in the level's registries: they reload with
        // /reload and live in the server's reloadable registries instead.
        var keys = Stream.of(
                        player.level().registryAccess(),
                        player.level().getServer().reloadableRegistries().lookup())
                .flatMap(registries -> registries.lookup(key).stream())
                .findFirst()
                .map(lookup -> lookup.listElementIds().map(ResourceKey::identifier).toList())
                .orElseGet(List::of);
        NetworkHelper.sendTo(player, new ASPayloads.SendRegistryKeys(payload.registry(), keys));
    }

    /// Runs a marker now. Running consumes it, so its settings go back into the player's inventory first, unless they already carry an identical copy.
    public static void generateFixture(ASPayloads.GenerateFixture payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.canUseGameMasterBlocks()) {
            return;
        }
        var level = player.level();
        if (!level.isLoaded(payload.pos()) || !(level.getBlockEntity(payload.pos()) instanceof FixtureBlockEntity marker)) {
            return;
        }
        var copy = marker.toItemStack(level.registryAccess());
        if (!player.getInventory().contains(copy)) {
            player.getInventory().placeItemBackInInventory(copy, Prediction.SERVER_ONLY);
        }
        FixtureGeneration.runNow(level, payload.pos());
    }

    /// Commits an authoring screen edit. Game master blocks are operator-only, so re-check here.
    public static void updateFixture(ASPayloads.UpdateFixture payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.canUseGameMasterBlocks()) {
            return;
        }
        if (!player.level().isLoaded(payload.pos())) {
            return;
        }
        if (!(player.level().getBlockEntity(payload.pos()) instanceof FixtureBlockEntity marker)) {
            return;
        }
        marker.setFixtureData(payload.fixtures(), player.level().registryAccess());
        marker.setCustomName(payload.customName().orElse(null));
        marker.setUseGravity(payload.useGravity());
        marker.setMarkPostProcessing(payload.markPostProcessing());
        if (FixtureBlockEntity.isValidOffset(payload.offset())) {
            marker.setOffset(payload.offset());
        }
        marker.setBecomes(payload.becomes());
        player.level().sendBlockUpdated(payload.pos(), marker.getBlockState(), marker.getBlockState(), Block.UPDATE_ALL);
    }
}
