package net.minecraftforge.actionable.commands.lib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraftforge.actionable.util.FunctionalInterfaces;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;
import org.kohsuke.github.ReactionContent;

import java.io.IOException;
import java.util.Set;

public record CommandManager(Set<String> prefixes, boolean allowEdits, GitHub gitHub, CommandDispatcher<GHCommandContext> dispatcher) {
    public void run(JsonNode payload) throws IOException {
        final ObjectReader reader = GitHubAccessor.objectReader(gitHub);

        final GHIssue issue = reader.forType(GHIssue.class).readValue(payload.get("issue"));
        final GHIssueComment comment = reader.forType(GHIssueComment.class).readValue(payload.get("comment"));
        GitHubAccessor.wrapUp(comment, issue);
        final String action = payload.get("action").asText();

        if (!this.shouldRunForEvent(action)) return;

        final String command = findCommand(comment.getBody());
        if (command == null) return;

        final GHCommandContext ctx = new GHCommandContext(gitHub, comment, issue, payload);
        final ParseResults<GHCommandContext> results = dispatcher.parse(command, ctx);

        // If the command does not fully parse, then return
        if (results.getReader().getRemainingLength() > 0) {
            return;
        }

        try {
            final int result = dispatcher.execute(results);
            if (result == Command.SINGLE_SUCCESS)
                ignoreExceptions(() -> comment.createReaction(ReactionContent.ROCKET));
        } catch (Exception e) {
            System.err.println("Error while executing command: " + command);
            e.printStackTrace();

            if (e instanceof CommandSyntaxException exception) {
                //noinspection deprecation
                ignoreExceptions(() -> issue.comment("@%s, I encountered an exception executing that command: %s".formatted(
                        comment.getUserName(), exception.getMessage()
                )));
            }

            ignoreExceptions(() -> comment.createReaction(ReactionContent.CONFUSED));
        }
    }

    public boolean shouldRunForEvent(final String action) {
        if (action.equals("created")) return true;
        if (action.equals("edited")) {
            return allowEdits;
        }
        return false;
    }

    public String findCommand(String comment) {
        for (final var prefix : this.prefixes) {
            String command = null;
            if (comment.startsWith(prefix)) {
                // If at the start, consider the entire comment a command
                command = comment.substring(prefix.length());
            } else if (comment.contains(prefix)) {
                final var index = comment.indexOf(prefix);
                // If anywhere else, consider the line a command
                final var newLineIndex = comment.indexOf('\n', index);
                if (newLineIndex >= 0) {
                    command = comment.substring(index + prefix.length(), newLineIndex);
                } else {
                    command = comment.substring(index + prefix.length());
                }
            }

            return command;
        }

        return null;
    }

    private static void ignoreExceptions(FunctionalInterfaces.RunnableException runnable) {
        try {
            runnable.run();
        } catch (IOException ignored) {}
    }
}
