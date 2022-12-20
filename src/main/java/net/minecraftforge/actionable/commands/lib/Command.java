package net.minecraftforge.actionable.commands.lib;

public record Command(
        boolean hasArgument,
        boolean runOnEdit,
        Check check,
        Callback callback
) {
    public interface Check {
        boolean canRun();
    }

    public interface Callback {
        void run(String args);
    }
}
