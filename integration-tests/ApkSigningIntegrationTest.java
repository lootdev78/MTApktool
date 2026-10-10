import com.android.apksig.ApkVerifier;
import io.github.abdurazaaqmohammed.apksigner.SignWrapper;
import io.github.lootdev78.mtcrypto.KeyStoreTools;
import java.io.File;
import java.nio.file.*;
import java.util.Comparator;

public final class ApkSigningIntegrationTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("mt-signing-check-");
        try {
            File key = directory.resolve("signer.jks").toFile();
            KeyStoreTools.generate(key, "JKS", "explicit-alias", "store-password".toCharArray(), "private-password".toCharArray(), "RSA", 2048, 365, "CN=Integration,C=DE");
            File output = directory.resolve("signed.apk").toFile();
            SignWrapper wrapper = new SignWrapper(key.getPath(), "store-password", "explicit-alias", "private-password", true, true, true, false);
            for (String input : args) {
                wrapper.signApk(new File(input), output);
                ApkVerifier.Result result = new ApkVerifier.Builder(output).setMinCheckedPlatformVersion(24).setMaxCheckedPlatformVersion(36).build().verify();
                if (!result.isVerified() || !result.isVerifiedUsingV2Scheme() || !result.isVerifiedUsingV3Scheme()) throw new AssertionError("APK verification failed for " + input + ": verified=" + result.isVerified() + ", v2=" + result.isVerifiedUsingV2Scheme() + ", v3=" + result.isVerifiedUsingV3Scheme() + ", errors=" + result.getErrors());
                if (!result.getSignerCertificates().get(0).equals(KeyStoreTools.keyEntry(KeyStoreTools.open(key, "store-password".toCharArray()), "explicit-alias", "private-password".toCharArray()).getCertificate())) throw new AssertionError("Wrong signing alias");
            }
            System.out.println("PASS existing apksig-android signs/verifies " + args.length + " APK(s) with generated JKS, explicit alias and separate private-key password");
        } finally { try (java.util.stream.Stream<Path> paths = Files.walk(directory)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) {} }); } }
    }
}
