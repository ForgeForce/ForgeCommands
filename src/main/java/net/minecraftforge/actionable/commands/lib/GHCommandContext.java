package net.minecraftforge.actionable.commands.lib;

import com.fasterxml.jackson.databind.JsonNode;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GitHub;

public record GHCommandContext(GitHub gitHub, GHIssueComment comment, GHIssue issue, JsonNode payload) {
}
