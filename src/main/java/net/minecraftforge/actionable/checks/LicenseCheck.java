package net.minecraftforge.actionable.checks;

import net.minecraftforge.actionable.util.DiffUtils;
import net.minecraftforge.actionable.util.FunctionalInterfaces;
import net.minecraftforge.actionable.util.GithubVars;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewEvent;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LicenseCheck {
    public static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".groovy", ".kt"
    );

    public static void check(GitHub gh, GHPullRequest pr) throws Exception {
        final Path repo = Path.of("license_checks");
        System.out.printf("Checking out %s in %s for license checks.%n", pr.getNumber(), repo);

        record Data(String body, String path) {}
        final List<Data> data = new ArrayList<>();

        final var head = pr.getHead();
        try (final Git git = Git.init().setDirectory(repo.toFile()).setGitDir(repo.resolve(".git").toFile()).call()) {
            git.remoteAdd().setName("head").setUri(new URIish(head.getRepository().getHtmlUrl() + ".git")).call();

            git.fetch().setRemote("head").call();
            git.checkout().setName(head.getSha()).call();

            final List<String> modifiedFiles = DiffUtils.detectNewFiles(GitHubAccessor.getDiff(pr).split("\n"));

            final Map<String, String> oldFiles = modifiedFiles.stream()
                    .filter(s -> TEXT_EXTENSIONS.stream().anyMatch(s::endsWith))
                    .collect(Collectors.toMap(Function.identity(), FunctionalInterfaces.throwingFunc(s -> Files.readString(repo.resolve(s)))));

            repo.resolve("gradlew").toFile().setExecutable(true);

            final ProcessBuilder proc = new ProcessBuilder()
                    .directory(repo.toFile())
                    .command("./gradlew", "updateLicenses")
                    .inheritIO();

            // Let's not leak secrets
            proc.environment().remove(GithubVars.GH_APP_KEY.getKey());
            proc.environment().remove(GithubVars.GH_APP_NAME.getKey());

            try (final Stream<Path> files = Files.find(Path.of(proc.environment().get("JAVA_HOME")), Integer.MAX_VALUE, (path, basicFileAttributes) -> true)) {
                files.forEach(System.out::println);
            }
            System.out.println(proc.environment().get("JAVA_HOME"));

            proc.start().waitFor();

            final Map<String, String> newFiles = modifiedFiles.stream()
                    .filter(s -> TEXT_EXTENSIONS.stream().anyMatch(s::endsWith))
                    .collect(Collectors.toMap(Function.identity(), FunctionalInterfaces.throwingFunc(s -> Files.readString(repo.resolve(s)))));

            for (final String file : newFiles.keySet()) {
                final String[] oldFile = oldFiles.get(file).split("\n");
                final String[] newFile = newFiles.get(file).split("\n");

                if (oldFile.length == 0 || newFile.length == 0 || newFile[0].equals(oldFile[0])) continue;

                // Else find the lines where the new file has modifications by finding the first index of the old file's first line
                final List<String> nf = List.of(newFile);
                final int idx = nf.indexOf(oldFile[0]);
                if (idx < 0) continue; // If we can't find it for whatever reason skip

                final List<String> addedLines = nf.subList(0, idx + 1);
                data.add(new Data("""
                        ```suggestion
                        %s
                        ```""".trim().formatted(String.join("\n", addedLines)), file));
            }
        }

        if (!data.isEmpty()) {
            final var review = pr.createReview()
                    .event(GHPullRequestReviewEvent.REQUEST_CHANGES)
                    .body("Missing license headers, please run the `updateLicenses` gradle task.")
                    .commitId(head.getSha());
            data.forEach(d -> review.comment(d.body, d.path, 1)); // Position is 1 because added files are always one hunk and licenses are added at the start
            review.create();
        }
    }
}
