package net.minecraftforge.actionable;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.authorization.JWTTokenProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println(Files.readString(Path.of(System.getenv("GITHUB_EVENT_PATH"))));

        System.out.println("\n");

        byte[] pkcs1Encoded = Base64.getDecoder().decode(args[1].replace("\n", ""));

        AlgorithmIdentifier algId = new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE);
        PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(algId, ASN1Sequence.getInstance(pkcs1Encoded));

        byte[] pkcs8Encoded = privateKeyInfo.getEncoded();

        final String key = (String) JWTTokenProvider.class.getDeclaredMethod("refreshJWT")
                .invoke(new JWTTokenProvider(args[0], KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8Encoded))));

        final var team = new GitHubBuilder()
                .withJwtToken(key)
                .build()
                .getOrganization("ForgeForce")
                .getTeamByName("triagers");

        System.out.println("So, the team members are: ");
        for (final var member : team.getMembers()) {
            System.out.println(member.getName());
        }
    }
}
