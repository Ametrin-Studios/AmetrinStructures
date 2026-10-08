package com.ametrin.structures;

import com.ametrin.structures.data.provider.ASBlockTagsProvider;
import com.ametrin.structures.data.provider.ASItemTagsProvider;
import com.ametrin.structures.data.provider.ASModelProvider;
import com.ametrin.structures.debug.SpreadCommand;
import com.ametrin.structures.debug.StructuresCommand;
import com.ametrin.structures.fixture.FixturePresets;
import com.ametrin.structures.foam.FoamBlock;
import com.ametrin.structures.foam.FoamPresets;
import com.ametrin.structures.network.ASPayloads;
import com.ametrin.structures.network.ServerPayloadHandlers;
import com.ametrin.structures.registry.*;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(AmetrinStructures.MOD_ID)
public class AmetrinStructures {
    public static final String MOD_ID = "ametrin_structures";

    public AmetrinStructures(IEventBus modBus, ModContainer container) {
        modBus.addListener(ASRegistries::register);
        modBus.addListener(ASRegistries::registerDatapackRegistries);
        modBus.addListener(AmetrinStructures::registerPayloads);
        modBus.addListener(ASDataComponents::registerTooltips);
        modBus.addListener(AmetrinStructures::addToCreativeTabs);
        modBus.addListener(AmetrinStructures::gatherData);
        NeoForge.EVENT_BUS.addListener(StructuresCommand::register);
        NeoForge.EVENT_BUS.addListener(SpreadCommand::forget);
        NeoForge.EVENT_BUS.addListener(FixturePresets::validateOnStart);
        NeoForge.EVENT_BUS.addListener(FoamBlock::fillOpening);

        ASDataComponents.REGISTER.register(modBus);
        ASBlocks.REGISTER.register(modBus);
        ASItems.REGISTER.register(modBus);
        ASBlockEntities.REGISTER.register(modBus);
        ASAttachments.REGISTER.register(modBus);

        ASFoamSpreadBehaviors.REGISTER.register(modBus);
        ASFoamSpreadRestrictions.REGISTER.register(modBus);
        ASProcessors.REGISTER.register(modBus);
        ASPoolElements.REGISTER.register(modBus);
        ASStructureTypes.REGISTER.register(modBus);
        ASPieceTypes.REGISTER.register(modBus);
        ASPlacementTypes.REGISTER.register(modBus);
        ASPieceSources.REGISTER.register(modBus);
        ASPlacementFilters.REGISTER.register(modBus);
        ASFixtures.REGISTER.register(modBus);
        ASFixtureConditions.REGISTER.register(modBus);
    }

    public static Identifier locate(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.OP_BLOCKS || !event.hasPermissions()) {
            return;
        }
        FoamPresets.ALL.forEach(preset -> event.accept(preset.createStack()));
        event.accept(Items.AMETHYST_SHARD);
        ASItems.REGISTER.getEntries().stream()
                .filter(item -> item != ASItems.FOAM)
                .forEach(item -> event.accept(item.get()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ASPayloads.PROTOCOL_VERSION);
        registrar.playToServer(
                ASPayloads.RequestRegistryKeys.TYPE,
                ASPayloads.RequestRegistryKeys.STREAM_CODEC,
                ServerPayloadHandlers::requestRegistryKeys);
        registrar.playToServer(
                ASPayloads.UpdateFixture.TYPE,
                ASPayloads.UpdateFixture.STREAM_CODEC,
                ServerPayloadHandlers::updateFixture);
        registrar.playToServer(
                ASPayloads.GenerateFixture.TYPE,
                ASPayloads.GenerateFixture.STREAM_CODEC,
                ServerPayloadHandlers::generateFixture);
        registrar.playToClient(ASPayloads.SendRegistryKeys.TYPE, ASPayloads.SendRegistryKeys.STREAM_CODEC);
        registrar.playToClient(ASPayloads.SpreadWaypoints.TYPE, ASPayloads.SpreadWaypoints.STREAM_CODEC);
        registrar.playToClient(ASPayloads.ClearSpreadWaypoints.TYPE, ASPayloads.ClearSpreadWaypoints.STREAM_CODEC);
    }

    private static void gatherData(GatherDataEvent.Client event) {
        event.createDatapackRegistryObjects(new RegistrySetBuilder().add(ASRegistries.SPAWNER_PROFILE, ASSpawnerProfiles::bootstrap));
        event.createProvider(ASModelProvider::new);
        event.createBlockAndItemTags(ASBlockTagsProvider::new, ASItemTagsProvider::new);
    }
}
