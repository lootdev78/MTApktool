import io.github.lootdev78.mtcrypto.KeyStoreTools;
import io.github.lootdev78.mtftp.*;
import java.io.*;
import java.net.ServerSocket;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** JVM integration checks for the protocol and keystore layers, without an Android SDK. */
public class CoreIntegrationTest {
    interface Checked { void run() throws Exception; }
    static int assertions;
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    static void rejects(Checked action, String message) throws Exception {
        try { action.run(); } catch (Exception expected) { assertions++; return; }
        throw new AssertionError(message);
    }
    static int port() throws IOException { try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); } }
    static void crypto(Path directory) throws Exception {
        char[] password = "store-password".toCharArray(), privatePassword = "private-password".toCharArray();
        for (String format : new String[]{"JKS", "PKCS12"}) for (String algorithm : new String[]{"RSA", "EC"}) {
            File file = directory.resolve(format + "-" + algorithm).toFile();
            KeyStoreTools.generate(file, format, "signing", password, privatePassword, algorithm, algorithm.equals("RSA") ? 2048 : 384, 12000, "CN=MTAPKTool,O=Integration,C=DE");
            KeyStore store = KeyStoreTools.open(file, password);
            KeyStore.PrivateKeyEntry entry = KeyStoreTools.keyEntry(store, "signing", privatePassword);
            X509Certificate cert = (X509Certificate)entry.getCertificate();
            cert.verify(cert.getPublicKey()); cert.checkValidity();
            check(cert.getPublicKey().getAlgorithm().equals(algorithm), "Certificate key algorithm");
            Signature signature = Signature.getInstance(algorithm.equals("RSA") ? "SHA256withRSA" : "SHA256withECDSA");
            signature.initSign(entry.getPrivateKey()); signature.update(new byte[]{1,2,3}); byte[] bytes = signature.sign();
            signature.initVerify(cert); signature.update(new byte[]{1,2,3}); check(signature.verify(bytes), "Private key matches certificate");
            KeyStore nativeStore = KeyStore.getInstance(format);
            try (InputStream input = new FileInputStream(file)) { nativeStore.load(input, password); }
            check(Arrays.equals(nativeStore.getKey("signing", privatePassword).getEncoded(), entry.getPrivateKey().getEncoded()), "Native JDK reads generated store");
            rejects(() -> KeyStoreTools.open(file, "wrong-password".toCharArray()), "Wrong store password must fail");
            rejects(() -> KeyStoreTools.keyEntry(store, "signing", "wrong-password".toCharArray()), "Wrong private key password must fail");
            rejects(() -> KeyStoreTools.generate(file, format, "signing", password, privatePassword, algorithm, 2048, 30, "CN=Overwrite"), "Generation must not overwrite");
            if (format.equals("JKS")) {
                nativeStore.setKeyEntry("second", entry.getPrivateKey(), privatePassword, entry.getCertificateChain());
                File imported = directory.resolve("native-" + algorithm + ".jks").toFile();
                try (OutputStream output = new FileOutputStream(imported)) { nativeStore.store(output, password); }
                KeyStore reopened = KeyStoreTools.open(imported, password);
                check(KeyStoreTools.keyAliases(reopened).size() == 2, "Portable JKS reads native multi-alias store");
                rejects(() -> KeyStoreTools.keyEntry(reopened, "", privatePassword), "Multi-key stores require explicit alias");
                check(KeyStoreTools.keyEntry(reopened, "second", privatePassword).getCertificateChain().length == 1, "Explicit alias loads chain");
            }
        }
        System.out.println("PASS keystore generation, native JKS/PKCS12 interoperability, RSA/EC signing, password and alias checks");
    }
    static void ftp(Path directory) throws Exception {
        Path root = Files.createDirectory(directory.resolve("ftp-root"));
        Path outside = Files.write(directory.resolve("outside.txt"), "outside".getBytes());
        Files.createSymbolicLink(root.resolve("escape"), directory);
        check(FtpPaths.normalize("/../../inside").equals("/inside"), "Virtual traversal stays rooted");
        rejects(() -> FtpPaths.confined(root.toFile(), "/escape/outside.txt"), "Symlinks cannot leave root");
        rejects(() -> FtpPaths.name("bad\r\nDELE /"), "Control-character names rejected");
        int port = port(); byte[] content = new byte[768 * 1024]; new Random(7).nextBytes(content);
        try (MtFtpServer server = new MtFtpServer(); MtFtpClient client = new MtFtpClient()) {
            server.start(root.toFile(), port, "mt", "password", true);
            rejects(() -> { try (MtFtpClient bad = new MtFtpClient()) { bad.connect("127.0.0.1", port, "mt", "wrong", MtFtpClient.Security.FTP, "/"); } }, "Bad authentication rejected");
            client.connect("127.0.0.1", port, "mt", "password", MtFtpClient.Security.FTP, "/");
            check(client.list("/").isEmpty(), "Escaping symlink not listed");
            client.mkdir("/Unicode ä"); client.upload("/Unicode ä/file.txt", new ByteArrayInputStream(content), false, n -> {});
            ByteArrayOutputStream output = new ByteArrayOutputStream(); client.download("/Unicode ä/file.txt", output, n -> {});
            check(Arrays.equals(content, output.toByteArray()), "Binary upload/download round trip");
            check(client.stat("/Unicode ä/file.txt").size == content.length, "Remote metadata");
            client.chmod("/Unicode ä/file.txt", 0640);
            check(client.permissions("/Unicode ä/file.txt") == 0640, "FTP permissions reflect actual Unix mode");
            check((((Number)Files.getAttribute(root.resolve("Unicode ä/file.txt"), "unix:mode")).intValue() & 07777) == 0640, "SITE CHMOD changes filesystem mode");
            rejects(() -> client.chmod("/escape/outside.txt", 0777), "Permission commands cannot leave shared root");
            rejects(() -> client.chmod("/", 0000), "Shared root permissions protected");
            client.rename("/Unicode ä/file.txt", "/Unicode ä/new.txt");
            check(client.stat("/Unicode ä/new.txt") != null, "Rename updates listing");
            client.upload("/Unicode ä/new.txt", new ByteArrayInputStream("replacement".getBytes()), true, n -> {});
            check(Files.readString(root.resolve("Unicode ä/new.txt")).equals("replacement"), "Overwrite publishes complete file");
            rejects(() -> client.upload("/Unicode ä/new.txt", new ByteArrayInputStream(content), true, n -> { if (n > 0) throw new IOException("cancel"); }), "Cancelled upload reports failure");
            check(Files.readString(root.resolve("Unicode ä/new.txt")).equals("replacement"), "Cancellation preserves existing destination");
            client.connect("127.0.0.1", port, "mt", "password", MtFtpClient.Security.FTP, "/");
            rejects(() -> client.delete("/"), "Root deletion denied");
            client.delete("/Unicode ä"); check(client.stat("/Unicode ä") == null, "Recursive delete");
            check(Files.exists(outside), "Outside file remains untouched");
        }
        int readOnlyPort = port();
        try (MtFtpServer server = new MtFtpServer(); MtFtpClient client = new MtFtpClient()) {
            server.start(root.toFile(), readOnlyPort, "mt", "password", false);
            client.connect("127.0.0.1", readOnlyPort, "mt", "password", MtFtpClient.Security.FTP, "/");
            rejects(() -> client.mkdir("/forbidden"), "Read-only server denies mutation");
            rejects(() -> client.chmod("/escape", 0777), "Read-only server denies chmod");
        }
        System.out.println("PASS FTP live server/client, authentication, Unicode, transfer, overwrite, cancellation, confinement and read-only mode");
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("mt-integration-");
        try { crypto(directory); ftp(directory); System.out.println("PASS " + assertions + " assertions"); }
        finally { try (java.util.stream.Stream<Path> paths = Files.walk(directory)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} }); } }
    }
}
