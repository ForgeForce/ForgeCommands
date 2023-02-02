package net.minecraftforge.actionable.event.pr_mirror;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import net.minecraftforge.actionable.Main;
import net.minecraftforge.actionable.event.EventHandler;
import net.minecraftforge.actionable.event.PushHandler;
import net.minecraftforge.actionable.util.GithubVars;
import net.minecraftforge.actionable.util.RepoConfig;
import net.minecraftforge.actionable.util.git.ExtensibleAddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.filter.NotTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

public class PRMirrorPush implements EventHandler {
    @Override
    public void handle(Main.GitHubGetter gitHubGetter, JsonNode payload) throws Throwable {
        final String branch = PushHandler.getBranchName(GithubVars.REF.get());
        if (branch == null) return; // Only check for commits pushed to branches
        final GitHub gh = gitHubGetter.get();

        final RepoConfig.PrivateMirror mirror = RepoConfig.INSTANCE.privateMirror();
        System.out.println(mirror);
        if (mirror == null) return;

        final ObjectReader reader = GitHubAccessor.objectReader(gh);

        final GHRepository repo = reader.readValue(payload.get("repository"), GHRepository.class);
        GHCommit lastCommit = reader.readValue(payload.get("commits"), GHCommit[].class)[0];
        lastCommit = repo.getCommit(last(lastCommit.getUrl().toString().split("/")));

        try (final Git git = Git.open(GithubVars.WORKSPACE.get())) {
            final var withCreds = getMirrorCreds(mirror, gh);
            final var cred = withCreds.credentials;

            final RemoteAddCommand addRemote = git.remoteAdd();
            addRemote.setName("mirror");
            addRemote.setUri(withCreds.location);
            addRemote.call();
            git.fetch().setCredentialsProvider(cred).setRemote("mirror").call();

            System.out.println(git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE)
                    .call().stream().map(Ref::getName).toList());
            final Ref remoteBranch = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE)
                    .call().stream().filter(it -> it.getName().equals("refs/remotes/mirror/" + branch))
                    .findFirst().orElse(null);
            final String branchName = branch + "_mirrored";

            if (remoteBranch != null) {
                git.branchCreate().setForce(false).setName(branchName).setStartPoint(remoteBranch.getObjectId().getName()).call();
            } else {
                git.branchCreate().setForce(false).setName(branchName).call();
            }

            git.checkout().setCreateBranch(true).setName("temp").call();
            git.reset().setMode(ResetCommand.ResetType.SOFT).setRef(branchName).call();
            git.checkout().setName(branchName).call();

            new ExtensibleAddCommand(git.getRepository())
                    .addPatterns(mirror.toAdd().stream().map(PathFilter::create).toList())
                    .addFilter(NotTreeFilter.create(PathFilter.create(".github/workflows/"))) // TODO - why doesn't this work?!
                    .addFilter(it -> true, false)
                    .call();

            final var shortCommit = lastCommit.getCommitShortInfo();
            final var author = shortCommit.getAuthor();
            git.commit()
                    .setMessage(stripNewline(shortCommit.getMessage()) + " - " + "Sync with " + lastCommit.getHtmlUrl() + "\nmirrorTarget:" + lastCommit.getSHA1())
                    .setAuthor(Main.APP_NAME, "pr-mirror.no-reply@minecraftforge.net")
                    .setCommitter(author.getName(), author.getEmail())
                    .call();
            git.push().setRemote("mirror").add(branchName + ":" + branch).setCredentialsProvider(cred).call();
        }
    }

    private static <T> T last(T[] array) {
        return array[array.length - 1];
    }

    static String stripNewline(String text) {
        final int idx = text.indexOf('\n');
        if (idx < 0) return text;
        return text.substring(0, idx);
    }

    static WithCredentials getMirrorCreds(RepoConfig.PrivateMirror mirror, GitHub gh) throws Throwable {
        final URIish location = new URIish("https://github.com/" + mirror.mirror() + ".git")
                .setUser("Actionable").setPass(GitHubAccessor.getToken(gh));
        return new WithCredentials(location, new UsernamePasswordCredentialsProvider("Actionable", location.getPass()));
    }

    public record WithCredentials(URIish location, UsernamePasswordCredentialsProvider credentials) {}
}
