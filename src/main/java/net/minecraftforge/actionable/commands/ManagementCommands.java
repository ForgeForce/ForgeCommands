package net.minecraftforge.actionable.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraftforge.actionable.commands.lib.GHCommandContext;

public class ManagementCommands {
    public static void register(CommandDispatcher<GHCommandContext> dispatcher) {
        dispatcher.register(literal("lock")
                .executes(wrap(ctx -> {
                    ctx.getSource().issue()
                            .lock();
                })));
    }

    private static LiteralArgumentBuilder<GHCommandContext> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
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
