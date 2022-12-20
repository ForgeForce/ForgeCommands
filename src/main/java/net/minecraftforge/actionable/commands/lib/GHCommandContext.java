package net.minecraftforge.actionable.commands.lib;

import com.google.gson.JsonObject;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GitHub;

public record GHCommandContext(GitHub gitHub, GHIssueComment comment, GHIssue issue, JsonObject payload) {
}
