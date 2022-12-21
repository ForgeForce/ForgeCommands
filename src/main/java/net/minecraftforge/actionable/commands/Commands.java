package net.minecraftforge.actionable.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraftforge.actionable.commands.lib.GHCommandContext;
import net.minecraftforge.actionable.util.FunctionalInterfaces;
import net.minecraftforge.actionable.util.GithubVars;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GitHub;

import java.util.function.Predicate;

public class Commands {
    public static void register(GitHub gh, CommandDispatcher<GHCommandContext> dispatcher) {
        ManagementCommands.register(gh, dispatcher);
    }

    public static Predicate<GHCommandContext> isInTeam(String team) {
        final String[] split = team.split("/");
        final String org = split.length == 1 ? GithubVars.REPOSITORY_OWNER.get() : split[0];
        final String name = split.length == 1 ? split[0] : split[1];
        return FunctionalInterfaces.wrapPred(ctx -> ctx
                .gitHub().getOrganization(org)
                .getTeamByName(name).hasMember(ctx.user()));
    }

    public static Predicate<GHCommandContext> hasPermission(GHPermissionType permission) {
        return FunctionalInterfaces.wrapPred(ctx -> ctx.issue().getRepository()
                .hasPermission(ctx.user(), permission));
    }
}
