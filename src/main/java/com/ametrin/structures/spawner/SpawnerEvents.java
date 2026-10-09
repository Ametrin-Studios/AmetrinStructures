package com.ametrin.structures.spawner;

import com.ametrin.structures.registry.ASAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.ApiStatus;

@EventBusSubscriber
@ApiStatus.Internal
public final class SpawnerEvents {
    private SpawnerEvents() {}

    /// A spawn egg used on a spawner clears the spawner profile, restoring vanilla behavior.
    @SubscribeEvent
    static void onUseOnSpawner(PlayerInteractEvent.RightClickBlock event) {
        // Only when vanilla will apply the egg; otherwise the profile would be lost for nothing.
        if (event.getLevel() instanceof ServerLevel level
                && level.isSpawnerBlockEnabled()
                && SpawnEggItem.getType(event.getItemStack()) != null
                && level.getBlockEntity(event.getPos()) instanceof SpawnerBlockEntity spawner) {
            spawner.removeData(ASAttachments.SPAWNER_PROFILE);
        }
    }

    /// A spawn egg retargets a spawner minecart the way it retargets a spawner block.
    @SubscribeEvent
    static void onUseOnSpawnerMinecart(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof MinecartSpawner cart) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var stack = event.getItemStack();
        var type = SpawnEggItem.getType(stack);
        if (type == null || !level.isSpawnerBlockEnabled()) {
            return;
        }
        var player = event.getEntity();
        cart.getSpawner().setEntityId(type, level, level.getRandom(), cart.blockPosition());
        cart.removeData(ASAttachments.SPAWNER_PROFILE);
        level.gameEvent(player, GameEvent.ENTITY_INTERACT, cart.position());
        stack.consume(1, player);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
