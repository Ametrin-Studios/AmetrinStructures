package com.ametrin.structures;

import com.ametrin.structures.client.*;
import com.ametrin.structures.registry.ASBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
        static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ASBlockEntities.FIXTURE.get(), FixtureRenderer::new);
            if (ASClientConfig.STRUCTURE_BLOCK_NAME_TAGS.getAsBoolean()) {
                event.registerBlockEntityRenderer(BlockEntityType.STRUCTURE_BLOCK, StructureBlockRenderer::new);
            }
            if (ASClientConfig.JIGSAW_NAME_TAGS.getAsBoolean()) {
                event.registerBlockEntityRenderer(BlockEntityType.JIGSAW, JigsawRenderer::new);
            }
        }
    }

    @EventBusSubscriber(modid = AmetrinStructures.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            RegistryKeyCache.clear();
        }

        @SubscribeEvent
        static void onItemTooltip(ItemTooltipEvent event) {
            FixtureTooltip.append(event);
        }
    }
}
