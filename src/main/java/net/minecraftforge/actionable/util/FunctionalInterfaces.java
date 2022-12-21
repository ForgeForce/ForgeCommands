package net.minecraftforge.actionable.util;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import java.io.IOException;
import java.util.function.Predicate;

public class FunctionalInterfaces {
    public static <T> Command<T> wrap(ConsException<CommandContext<T>> consumer) {
        return context -> {
            try {
                consumer.accept(context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return Command.SINGLE_SUCCESS;
        };
    }

    public static <T> Predicate<T> wrapPred(PredException<T> pred) {
        return context -> {
            try {
                return pred.test(context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public interface ConsException<T> {
        void accept(T t) throws Exception;
    }

    public interface PredException<T> {
        boolean test(T t) throws Exception;
    }

    public interface RunnableException {
        void run() throws IOException;
    }
}
