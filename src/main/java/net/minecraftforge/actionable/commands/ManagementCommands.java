package net.minecraftforge.actionable.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraftforge.actionable.commands.lib.EnumArgumentType;
import net.minecraftforge.actionable.commands.lib.GHCommandContext;
import net.minecraftforge.actionable.commands.lib.RepoArgumentType;
import net.minecraftforge.actionable.util.GithubVars;
import org.kohsuke.github.GHPermissionType;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;
import org.kohsuke.github.LockReason;

import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import static net.minecraftforge.actionable.util.FunctionalInterfaces.*;
import static net.minecraftforge.actionable.commands.Commands.*;

public class ManagementCommands {
    public static void register(GitHub gh, CommandDispatcher<GHCommandContext> dispatcher) {
        final Predicate<GHCommandContext> canManage = isInTeam(GithubVars.TRIAGE_TEAM.get()); /* .or(hasPermission(GHPermissionType.WRITE)); */

        dispatcher.register(literal("lock")
                .requires(canManage)
                .executes(wrap(ctx -> ctx.getSource().issue().lock()))
                .then(argument("reason", EnumArgumentType.lowerCaseEnum(StringArgumentType.greedyString(), LockReason.class, Map.of("too heated", LockReason.TOO_HEATED, "off topic", LockReason.OFF_TOPIC)))
                        .executes(wrap(ctx -> GitHubAccessor.lock(ctx.getSource().issue(), ctx.getArgument("reason", LockReason.class))))));

        dispatcher.register(literal("move")
                .requires(canManage.and(it -> !it.issue().isPullRequest()))
                .then(argument("repo", RepoArgumentType.repo(gh, GithubVars.REPOSITORY_OWNER.get())))
                        .executes(wrap(ctx -> {
                            final GHRepository repo = ctx.getArgument("repo", GHRepository.class);
                            GitHubAccessor.graphQl(
                                    ctx.getSource().gitHub(),
                                    """
                                    mutation {
                                      transferIssue(input: {
                                        clientMutationId: "%s",
                                        repositoryId: "%s",
                                        issueId: "%s"
                                      }) {
                                        issue {
                                          number
                                        }
                                      }
                                    }""".formatted(
                                        randomHex(),
                                        repo.getNodeId(),
                                        ctx.getSource().issue().getNodeId()
                                    )
                            );
                        })));

        dispatcher.register(literal("shipit")
                .requires(hasPermission(GHPermissionType.WRITE).and(ctx -> ctx.issue().isPullRequest()))
                .executes(wrap(ctx -> {
                    final GHPullRequest pr = ctx.getSource().pullRequest();
                    final String title = pr.getTitle() + " (#" + pr.getNumber() + ")";
                    GitHubAccessor.merge(pr, title, null, GHPullRequest.MergeMethod.SQUASH);
                })));
    }

    private static LiteralArgumentBuilder<GHCommandContext> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<GHCommandContext, T> argument(final String name, final ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static String randomHex() {
        return Integer.toHexString(new Random().nextInt(1_000_000_000));
    }

}
