package net.minecraftforge.actionable.event.pr_mirror;

import com.fasterxml.jackson.databind.JsonNode;
import net.minecraftforge.actionable.Main;
import net.minecraftforge.actionable.event.ByActionEventHandler;
import net.minecraftforge.actionable.util.FunctionalInterfaces;
import net.minecraftforge.actionable.util.GithubVars;
import net.minecraftforge.actionable.util.RepoConfig;
import net.minecraftforge.actionable.util.enums.Action;
import net.minecraftforge.actionable.util.git.ExtensibleAddCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.filter.NotTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.util.stream.StreamSupport;

public class PRMirrorPRTarget extends ByActionEventHandler<PRMirrorPRTarget.Payload> {

    public PRMirrorPRTarget() {
        super(Payload.class, payload -> GitHubAccessor.wrapUp(payload.pull_request, payload.repository), payloadRegistrar -> payloadRegistrar
                .register(Action.OPENED, PRMirrorPRTarget::onCreate)
                .register(Action.SYNCHRONIZE, PRMirrorPRTarget::onSync));
    }

    public record Payload(GHPullRequest pull_request, GHRepository repository, GHOrganization organization) {}

    private static void onCreate(GitHub gitHub, Payload payload, JsonNode node) throws Throwable {
        final RepoConfig.PrivateMirror mirror = RepoConfig.INSTANCE.privateMirror();
        System.out.println(mirror);
        if (mirror == null) return;
        try (final Git git = Git.open(GithubVars.WORKSPACE.get())) {
            final var withCreds = PRMirrorPush.getMirrorCreds(mirror, gitHub);
            final var cred = withCreds.credentials();

            final RemoteAddCommand addRemote = git.remoteAdd();
            addRemote.setName("mirror");
            addRemote.setUri(withCreds.location());
            addRemote.call();

            git.checkout()
                    .setName("mirror/" + payload.pull_request.getBase().getRef())
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK);

            final String prBase = payload.pull_request.getBase().getSha();
            final RevCommit baseCommit = StreamSupport.stream(git.log().call().spliterator(), false)
                    .filter(it -> it.getFullMessage().contains("mirrorTarget:" + prBase))
                    .findFirst().orElseGet(FunctionalInterfaces.supEx(() -> git.log().setMaxCount(1).call().iterator().next())::uncheck);
            git.checkout()
                    .setCreateBranch(true)
                    .setName("pr_" + payload.pull_request.getNumber())
                    .setStartPoint(baseCommit)
                    .call();
            git.push()
                    .setRemote("mirror")
                    .add("pr_" + payload.pull_request.getNumber() + ":" + "pr-" + payload.pull_request.getNumber())
                    .setCredentialsProvider(cred)
                    .call();
        }

        final GHRepository mirrorRepo = gitHub.getRepository(mirror.mirror());
        mirrorRepo.createPullRequest(
                payload.pull_request.getNumber() + " - " + payload.pull_request.getTitle(),
                "pr-" + payload.pull_request.getNumber(),
                payload.pull_request.getBase().getRef(),
                payload.pull_request.getHtmlUrl().toString()
        );
        // TODO - we need to update the branch as soon as we create the PR
    }

    private static void onSync(GitHub gitHub, Payload payload, JsonNode node) throws Throwable {
        final RepoConfig.PrivateMirror mirror = RepoConfig.INSTANCE.privateMirror();
        if (mirror == null) return;

        try (final Git git = Git.open(GithubVars.WORKSPACE.get())) {
            final var withCreds = PRMirrorPush.getMirrorCreds(mirror, gitHub);
            final var cred = withCreds.credentials();

            final RemoteAddCommand addRemote = git.remoteAdd();
            addRemote.setName("mirror");
            addRemote.setUri(withCreds.location());
            addRemote.call();
            git.fetch().setCredentialsProvider(cred).setRemote("mirror").call();

            final var head = payload.pull_request.getHead().getCommit();
            final Ref remoteBranch = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE)
                    .call().stream().filter(it -> it.getName().equals("refs/remotes/mirror/pr-" + payload.pull_request.getNumber()))
                    .findFirst().orElse(null);

            git.branchCreate().setForce(false).setName("pr_" + payload.pull_request.getNumber()).setStartPoint(remoteBranch.getObjectId().getName()).call();
            git.checkout().setCreateBranch(true).setName("temp").call();
            git.reset().setMode(ResetCommand.ResetType.SOFT).setRef("pr_" + payload.pull_request.getNumber()).call();
            git.checkout().setCreateBranch(true).setName("pr_" + payload.pull_request.getNumber()).call();

            new ExtensibleAddCommand(git.getRepository())
                    .addPatterns(mirror.toAdd().stream().map(PathFilter::create).toList())
                    .addFilter(NotTreeFilter.create(PathFilter.create(".github/workflows/"))) // TODO - why doesn't this work?!
                    .addFilter(it -> true, false)
                    .call();

            final var shortCommit = head.getCommitShortInfo();
            final var author = shortCommit.getAuthor();
            git.commit()
                    .setMessage(PRMirrorPush.stripNewline(shortCommit.getMessage()) + " - " + "Sync with " + head.getHtmlUrl())
                    .setAuthor(Main.APP_NAME, "pr-mirror.no-reply@minecraftforge.net")
                    .setCommitter(author.getName(), author.getEmail())
                    .call();

            git.push()
                    .setCredentialsProvider(cred)
                    .setRemote("mirror")
                    .add("pr_" + payload.pull_request.getNumber() + ":pr-" + payload.pull_request.getNumber())
                    .call();
        }
    }

}
