package net.minecraftforge.actionable.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraftforge.actionable.commands.lib.GHCommandContext;

public class Commands {
    public static void register(CommandDispatcher<GHCommandContext> dispatcher) {
        ManagementCommands.register(dispatcher);
    }
}
