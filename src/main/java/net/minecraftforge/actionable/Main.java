package net.minecraftforge.actionable;

import com.fasterxml.jackson.databind.ObjectReader;
import com.google.gson.JsonObject;
import net.minecraftforge.actionable.util.AuthUtil;
import net.minecraftforge.actionable.util.GithubVars;
import net.minecraftforge.actionable.util.Jsons;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.authorization.AuthorizationProvider;

import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

public class Main {


    public static void main(String[] args) throws Throwable {
        final JsonObject payload = Jsons.get(Path.of(GithubVars.EVENT_PATH.get()), JsonObject.class);

        final PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(AuthUtil.parsePKCS8(args[1])));
        final String appId = args[0];

        final AuthorizationProvider authorizationProvider = AuthUtil.jwt(appId, key, app ->
                app.getInstallationByOrganization(GithubVars.REPOSITORY_OWNER.get())
                        .createToken().create());

        final var team = new GitHubBuilder()
                .withAuthorizationProvider(authorizationProvider)
                .build()
                .getOrganization("ForgeForce")
                .getTeamByName("triagers");

        System.out.println("So, the team members are: ");
        for (final var member : team.getMembers()) {
            System.out.println(member.getLogin());
        }
    }

}
