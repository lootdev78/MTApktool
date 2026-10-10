package io.github.lootdev78.mtcrypto;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.security.auth.x500.X500Principal;

/** Key generation, certificate inspection and keystore loading shared with apksig-android. */
public final class KeyStoreTools {
    private KeyStoreTools() {}
    public static KeyStore open(File file, char[] password) throws Exception {
        if (!file.isFile() || file.length() > 16 * 1024 * 1024) throw new IOException("Keystore missing or too large");
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            if (input.readInt() == 0xfeedfeed) {
                KeyStore store = PortableJks.create(); try (InputStream stream = new FileInputStream(file)) { store.load(stream, password); } return store;
            }
        }
        Exception last = null;
        for (String type : new String[]{"PKCS12", "BKS", "JKS"}) try (InputStream input = new FileInputStream(file)) {
            KeyStore store = KeyStore.getInstance(type); store.load(input, password); return store;
        } catch (Exception e) { if (last == null) last = e; }
        throw new IOException("Keystore password incorrect, damaged file or unsupported format", last);
    }
    public static List<String> keyAliases(KeyStore store) throws KeyStoreException {
        List<String> result = new ArrayList<>(); Enumeration<String> aliases = store.aliases();
        while (aliases.hasMoreElements()) { String alias = aliases.nextElement(); if (store.isKeyEntry(alias)) result.add(alias); }
        Collections.sort(result); return result;
    }
    public static KeyStore.PrivateKeyEntry keyEntry(KeyStore store, String alias, char[] keyPassword) throws Exception {
        List<String> names = keyAliases(store);
        if (alias == null || alias.isEmpty()) {
            if (names.size() != 1) throw new KeyStoreException("Select a private-key alias explicitly"); alias = names.get(0);
        }
        if (!store.isKeyEntry(alias)) throw new KeyStoreException("Alias is not a private key");
        Key key = store.getKey(alias, keyPassword); Certificate[] chain = store.getCertificateChain(alias);
        if (!(key instanceof PrivateKey) || chain == null || chain.length == 0) throw new KeyStoreException("Private key or certificate missing");
        return new KeyStore.PrivateKeyEntry((PrivateKey)key, chain);
    }
    public static void generate(File destination, String format, String alias, char[] storePassword, char[] keyPassword,
            String algorithm, int bits, int validityDays, String distinguishedName) throws Exception {
        if (destination.exists()) throw new IOException("Destination already exists");
        if (alias == null || alias.trim().isEmpty() || alias.length() > 80 || alias.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException("Invalid alias");
        if (storePassword.length < 6 || keyPassword.length < 6) throw new IllegalArgumentException("Passwords require at least six characters");
        if (validityDays < 1 || validityDays > 36500) throw new IllegalArgumentException("Validity must be 1–36500 days");
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        if (algorithm.equals("RSA")) { if (bits != 2048 && bits != 3072 && bits != 4096) throw new IllegalArgumentException("RSA requires 2048, 3072 or 4096 bits"); generator.initialize(bits, new SecureRandom()); }
        else if (algorithm.equals("EC")) { if (bits != 256 && bits != 384) throw new IllegalArgumentException("EC requires 256 or 384 bits"); generator.initialize(new ECGenParameterSpec(bits == 384 ? "secp384r1" : "secp256r1"), new SecureRandom()); }
        else throw new IllegalArgumentException("Choose RSA or EC");
        KeyPair pair = generator.generateKeyPair();
        X509Certificate certificate = certificate(pair, validityDays, new X500Principal(distinguishedName));
        KeyStore store = format.equals("JKS") ? PortableJks.create() : KeyStore.getInstance("PKCS12");
        store.load(null, storePassword); store.setKeyEntry(alias, pair.getPrivate(), keyPassword, new Certificate[]{certificate});
        File parent = destination.getParentFile(); if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create key folder");
        File temporary = File.createTempFile(".mt-key-", ".tmp", parent);
        try {
            try (OutputStream output = new FileOutputStream(temporary)) { store.store(output, storePassword); }
            keyEntry(open(temporary, storePassword), alias, keyPassword);
            Files.move(temporary.toPath(), destination.toPath());
        } finally { temporary.delete(); }
    }
    private static X509Certificate certificate(KeyPair pair, int days, X500Principal subject) throws Exception {
        boolean rsa = pair.getPrivate().getAlgorithm().equals("RSA");
        String signatureName = rsa ? "SHA256withRSA" : "SHA256withECDSA";
        byte[] algorithm = rsa ? Der.sequence(Der.oid("1.2.840.113549.1.1.11"), Der.tag(5, new byte[0])) : Der.sequence(Der.oid("1.2.840.10045.4.3.2"));
        long now = System.currentTimeMillis();
        byte[] tbs = Der.sequence(Der.tag(0xa0, Der.integer(BigInteger.valueOf(2))), Der.integer(new BigInteger(128, new SecureRandom()).add(BigInteger.ONE)),
            algorithm, subject.getEncoded(), Der.sequence(time(new Date(now - 60000)), time(new Date(now + days * 86400000L))), subject.getEncoded(), pair.getPublic().getEncoded());
        Signature signature = Signature.getInstance(signatureName); signature.initSign(pair.getPrivate()); signature.update(tbs);
        X509Certificate result = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Der.sequence(tbs, algorithm, Der.bitString(signature.sign()))));
        result.verify(pair.getPublic()); result.checkValidity(); return result;
    }
    private static byte[] time(Date date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")); calendar.setTime(date); boolean general = calendar.get(Calendar.YEAR) >= 2050;
        SimpleDateFormat format = new SimpleDateFormat(general ? "yyyyMMddHHmmss'Z'" : "yyMMddHHmmss'Z'", Locale.ROOT); format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return Der.tag(general ? 0x18 : 0x17, format.format(date).getBytes(StandardCharsets.US_ASCII));
    }
    public static String certificateInfo(X509Certificate certificate) throws Exception {
        return "Subject: " + certificate.getSubjectX500Principal() + "\nIssuer: " + certificate.getIssuerX500Principal() + "\nSerial: " + certificate.getSerialNumber().toString(16)
            + "\nValid: " + certificate.getNotBefore() + " – " + certificate.getNotAfter() + "\nAlgorithm: " + certificate.getPublicKey().getAlgorithm() + " / " + certificate.getSigAlgName()
            + "\nSHA-256: " + fingerprint(certificate, "SHA-256") + "\nSHA-1: " + fingerprint(certificate, "SHA-1");
    }
    public static String fingerprint(X509Certificate certificate, String algorithm) throws Exception {
        byte[] digest = MessageDigest.getInstance(algorithm).digest(certificate.getEncoded()); StringJoiner result = new StringJoiner(":");
        for (byte value : digest) result.add(String.format(Locale.ROOT, "%02X", value & 255)); return result.toString();
    }
    public static String pem(X509Certificate certificate) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(certificate.getEncoded()) + "\n-----END CERTIFICATE-----\n";
    }
    public static String escapeDn(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (",+\"\\<>;=".indexOf(c) >= 0 || i == 0 && c == '#' || c == ' ' && (i == 0 || i == value.length() - 1)) escaped.append('\\'); escaped.append(c); }
        return escaped.toString();
    }
}
