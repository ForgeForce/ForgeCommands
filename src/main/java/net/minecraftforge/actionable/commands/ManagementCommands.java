package net.minecraftforge.actionable.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraftforge.actionable.commands.lib.EnumArgumentType;
import net.minecraftforge.actionable.commands.lib.GHCommandContext;
import org.kohsuke.github.GitHubAccessor;
import org.kohsuke.github.LockReason;

import java.util.Map;

public class ManagementCommands {
    public static void register(CommandDispatcher<GHCommandContext> dispatcher) {
        dispatcher.register(literal("lock")
                .executes(wrap(ctx -> GitHubAccessor.lock(ctx.getSource().issue(), LockReason.RESOLVED)))
                .then(argument("reason", EnumArgumentType.lowerCaseEnum(StringArgumentType.greedyString(), LockReason.class, Map.of("too heated", LockReason.TOO_HEATED, "off topic", LockReason.OFF_TOPIC)))
                        .executes(wrap(ctx -> GitHubAccessor.lock(ctx.getSource().issue(), ctx.getArgument("reason", LockReason.class))))));
    }

    private static LiteralArgumentBuilder<GHCommandContext> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<GHCommandContext, T> argument(final String name, final ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static <T> Command<T> wrap(ConsException<CommandContext<T>> consumer) {
        return context -> {
            try {
                consumer.accept(context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return Command.SINGLE_SUCCESS;
        };
    }

    public interface ConsException<T> {
        void accept(T t) throws Exception;
    }

}
