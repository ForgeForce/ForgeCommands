package net.minecraftforge.actionable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import net.minecraftforge.actionable.util.Action;
import net.minecraftforge.actionable.util.FunctionalInterfaces;
import net.minecraftforge.actionable.util.GithubVars;
import net.minecraftforge.actionable.util.Jsons;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PRCreateAction {
    public static void run(Main.GitHubGetter gitHubGetter, JsonNode payload) throws Throwable {
        final Action action = Action.get(payload);
        if (action != Action.OPENED) return; // Only run on PR open

        final GitHub gitHub = gitHubGetter.get();
        final ObjectReader reader = GitHubAccessor.objectReader(gitHub);

        final GHPullRequest pullRequest = reader.forType(GHPullRequest.class).readValue(payload.get("pull_request"));
        final GHRepository repository = reader.forType(GHRepository.class).readValue(payload.get("repository"));
        GitHubAccessor.wrapUp(pullRequest, repository);

        final GHOrganization organization = gitHub.getOrganization(repository.getOwnerName());

        // We split into steps to not crash if someone does one of the steps manually
        final Set<FunctionalInterfaces.RunnableException> steps = new HashSet<>();

        steps.add(() -> GitHubAccessor.addLabel(pullRequest, "Triage"));
        steps.add(() -> pullRequest.requestTeamReviewers(List.of(
                organization.getTeamByName(GithubVars.TRIAGE_TEAM.get())
        )));

        steps.add(() -> addToProject(gitHub, organization, GithubVars.TRIAGE_PROJECT.get(), pullRequest));

        steps.forEach(FunctionalInterfaces::ignoreExceptions);
    }

    private static void addToProject(GitHub gitHub, GHOrganization organization, int projectURL, GHPullRequest pullRequest) throws IOException {
        final JsonNode idQuery = GitHubAccessor.graphQl(gitHub, """
                query{
                    organization(login: "%s"){
                      projectV2(number: %s) {
                        id
                      }
                    }
                  }""".formatted(
                organization.getLogin(),
                projectURL
        ));
        final String projectId = Jsons.at(idQuery, "data.organization.projectV2.id").asText();
        GitHubAccessor.graphQl(gitHub, """
            mutation {
            addProjectV2ItemById(input: {projectId: "%s" contentId: "%s"}) {
                item {
                  id
                }
              }
            }""",
            projectId, pullRequest.getNodeId()
        );
    }
}