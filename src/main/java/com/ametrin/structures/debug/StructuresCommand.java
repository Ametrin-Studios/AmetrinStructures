package com.ametrin.structures.debug;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/// `/ametrin structures …`: the library's commands, under the Ametrin API's `/ametrin`. Operators only.
public final class StructuresCommand {
    private StructuresCommand() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // The permission is on `structures`, not on the shared root: brigadier merges a node registered twice
        // and keeps the first registration's requirement, which would then hold for the API's commands too.
        var structures = Commands.literal("structures")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(SpreadCommand.spread())
                .then(SpreadCommand.visit())
                .then(CheckCommand.check())
                .then(SaveStructuresCommand.saveStructures());
        ExportTemplatesCommand.exportTemplates().ifPresent(structures::then);
        dispatcher.register(Commands.literal("ametrin").then(structures));
    }
}
