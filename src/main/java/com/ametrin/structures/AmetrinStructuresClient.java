package com.ametrin.structures;

import com.ametrin.structures.client.*;
import com.ametrin.structures.client.compat.SpreadWaypointsClient;
import com.ametrin.structures.network.ASPayloads;
import com.ametrin.structures.registry.ASBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.ApiStatus;

@Mod(value = AmetrinStructures.MOD_ID, dist = Dist.CLIENT)
@ApiStatus.Internal
public class AmetrinStructuresClient {
    public AmetrinStructuresClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ASClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @EventBusSubscriber(modid = AmetrinStructures.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
            event.register(
                    ASPayloads.SendRegistryKeys.TYPE,
                    (payload, context) -> RegistryKeyCache.accept(payload.registry(), payload.keys()));
            event.register(ASPayloads.SpreadWaypoints.TYPE, (payload, context) -> SpreadWaypointsClient.show(payload));
            event.register(ASPayloads.ClearSpreadWaypoints.TYPE, (payload, context) -> SpreadWaypointsClient.clear());
        }

        @SubscribeEvent
        static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ASBlockEntities.FIXTURE.get(), FixtureRenderer::new);
            if (ASClientConfig.STRUCTURE_BLOCK_NAME_TAGS.getAsBoolean()) {
                event.registerBlockEntityRenderer(BlockEntityTypes.STRUCTURE_BLOCK, _ -> new StructureBlockRenderer());
            }
        }
    }

    @EventBusSubscriber(modid = AmetrinStructures.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            // dropping server-provided state on disconnect.
            RegistryKeyCache.clear();
        }

        @SubscribeEvent
        static void onItemTooltip(ItemTooltipEvent event) {
            FixtureTooltip.append(event);
        }
    }
}
