package net.minecraftforge.actionable;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.authorization.JWTTokenProvider;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public class Main {
    public static void main(String[] args) throws Throwable {
        // System.out.println(Files.readString(Path.of(System.getenv("GITHUB_EVENT_PATH"))));

        byte[] pkcs1Encoded = Base64.getDecoder().decode(args[1].replaceAll("\\s", ""));

        AlgorithmIdentifier algId = new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE);
        PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(algId, ASN1Sequence.getInstance(pkcs1Encoded));

        byte[] pkcs8Encoded = privateKeyInfo.getEncoded();

        final String botKey = refreshJWT(args[0], KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8Encoded)));
        final GHAppInstallationToken authKey = new GitHubBuilder()
                .withJwtToken(botKey)
                .build().getApp().getInstallationByOrganization("ForgeForce")
                .createToken().create();


        final var team = new GitHubBuilder()
                .withJwtToken(authKey.getToken())
                .build()
                .getOrganization("ForgeForce")
                .getTeamByName("triagers");

        System.out.println("So, the team members are: ");
        for (final var member : team.getMembers()) {
            System.out.println(member.getLogin());
        }
    }

    /* public static void main(String[] args) throws Throwable {
        final var arg = "";
        byte[] pkcs1Encoded = Base64.getDecoder().decode(arg.replaceAll("\\s", ""));

        AlgorithmIdentifier algId = new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE);
        PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(algId, ASN1Sequence.getInstance(pkcs1Encoded));

        byte[] pkcs8Encoded = privateKeyInfo.getEncoded();

        final String key = refreshJWT("248343", KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8Encoded)));

        System.out.println(key);

        final var gh = new GitHubBuilder()
                .withJwtToken(key)
                .build();
        System.out.println(gh.getApp().getInstallationByOrganization("ForgeForce"));
        System.out.println(gh.getMyOrganizations());
        final var team = gh
                .getOrganization("ForgeForce")
                .getTeamByName("triagers");

        System.out.println("So, the team members are: ");
        for (final var member : team.getMembers()) {
            System.out.println(member.getName());
        }
    } */

    private static String refreshJWT(String appId, PrivateKey privateKey) {
        Instant now = Instant.now();

        // Max token expiration is 10 minutes for GitHub
        // We use a smaller window since we likely will not need more than a few seconds
        Instant expiration = now.plus(Duration.ofMinutes(9));

        // Setting the issued at to a time in the past to allow for clock skew
        Instant issuedAt = getIssuedAt(now);

        // Let's set the JWT Claims
        JwtBuilder builder = Jwts.builder()
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiration))
                .setIssuer(appId)
                .signWith(privateKey, SignatureAlgorithm.RS256);

        // Builds the JWT and serializes it to a compact, URL-safe string
        return builder.compact();
    }

    static Instant getIssuedAt(Instant now) {
        return now.minus(Duration.ofMinutes(2));
    }
}
