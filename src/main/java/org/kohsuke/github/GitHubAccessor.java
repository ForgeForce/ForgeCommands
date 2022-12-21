package org.kohsuke.github;

import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;

public class GitHubAccessor {
    public static ObjectReader objectReader(GitHub gitHub) {
        return GitHubClient.getMappingObjectReader(gitHub);
    }

    public static void wrapUp(GHIssueComment comment, GHIssue owner) {
        comment.wrapUp(owner);
    }

    public static void lock(GHIssue issue, LockReason reason) throws IOException {
        issue.root().createRequest().method("PUT").withUrlPath(issue.getApiRoute() + "/lock")
                .inBody().with("lock_reason", reason.toString()).send();
    }
}
