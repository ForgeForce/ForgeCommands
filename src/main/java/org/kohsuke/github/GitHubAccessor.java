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

    public static void merge(GHPullRequest pr, String title, String message, GHPullRequest.MergeMethod method) throws IOException {
        pr.root().createRequest()
                .method("PUT")
                .with("commit_message", message == null ? "" : message)
                .with("commit_title", title)
                .with("sha", pr.getHead().getSha())
                .with("merge_method", method)
                .withUrlPath(pr.getApiRoute() + "/merge")
                .send();
    }

    public static void graphQl(GitHub gitHub, String query) throws IOException {
        gitHub.createRequest()
                .method("POST")
                .inBody()
                .with("query", query)
                .withUrlPath("/graphql")
                .send();
    }
}
