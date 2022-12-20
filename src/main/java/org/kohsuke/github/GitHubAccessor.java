package org.kohsuke.github;

import com.fasterxml.jackson.databind.ObjectReader;

public class GitHubAccessor {
    public static ObjectReader objectReader(GitHub gitHub) {
        return GitHubClient.getMappingObjectReader(gitHub);
    }

    public static void wrapUp(GHIssueComment comment, GHIssue owner) {
        comment.wrapUp(owner);
    }
}
