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
        // The permission check is on `structures`, not the shared root. Brigadier merges nodes registered twice
        // and keeps the first requirement, so a check on the root would also apply to the API's commands.
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
