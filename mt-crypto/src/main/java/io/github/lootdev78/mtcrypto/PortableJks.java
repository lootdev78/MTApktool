package io.github.lootdev78.mtcrypto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/** Standard JKS reader/writer for Android runtimes which do not ship a JKS provider. */
public final class PortableJks extends KeyStoreSpi {
    private static final int MAGIC = 0xfeedfeed, MAX_BYTES = 16 * 1024 * 1024;
    private static final String PROTECTION_OID = "1.3.6.1.4.1.42.2.17.1.1";
    private static final class Entry { long time; byte[] key; Certificate[] chain; }
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    public PortableJks() {}
    public static KeyStore create() throws KeyStoreException {
        Provider provider = new Provider("MTApktoolJKS", 1.0, "Portable standard JKS") {};
        provider.put("KeyStore.JKS", PortableJks.class.getName());
        return KeyStore.getInstance("JKS", provider);
    }
    private static String alias(String value) { return value.toLowerCase(Locale.ROOT); }
    private static byte[] passwordBytes(char[] password) {
        char[] p = password == null ? new char[0] : password; byte[] bytes = new byte[p.length * 2];
        for (int i = 0; i < p.length; i++) { bytes[i * 2] = (byte)(p[i] >> 8); bytes[i * 2 + 1] = (byte)p[i]; } return bytes;
    }
    private static byte[] digest(byte[]... values) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1"); for (byte[] value : values) digest.update(value); return digest.digest();
    }
    private static byte[] protect(byte[] key, char[] password) throws GeneralSecurityException {
        byte[] pass = passwordBytes(password), salt = new byte[20]; new SecureRandom().nextBytes(salt);
        byte[] encrypted = key.clone(), block = salt;
        for (int offset = 0; offset < encrypted.length; offset += 20) {
            block = digest(pass, block); for (int n = 0; n < Math.min(20, encrypted.length - offset); n++) encrypted[offset + n] ^= block[n];
        }
        byte[] result = Der.sequence(Der.sequence(Der.oid(PROTECTION_OID), Der.tag(5, new byte[0])), Der.octets(Der.join(salt, encrypted, digest(pass, key))));
        Arrays.fill(pass, (byte)0); return result;
    }
    private static byte[] unprotect(byte[] encoded, char[] password) throws GeneralSecurityException, IOException {
        Der.Reader outer = new Der.Reader(new Der.Reader(encoded).read(0x30));
        Der.Reader algorithm = new Der.Reader(outer.read(0x30));
        if (!Arrays.equals(algorithm.read(6), new Der.Reader(Der.oid(PROTECTION_OID)).read(6))) throw new IOException("Unsupported JKS key protection");
        byte[] bytes = outer.read(4);
        if (bytes.length < 40) throw new IOException("Invalid protected JKS key");
        byte[] pass = passwordBytes(password), block = Arrays.copyOf(bytes, 20), key = Arrays.copyOfRange(bytes, 20, bytes.length - 20);
        for (int offset = 0; offset < key.length; offset += 20) {
            block = digest(pass, block); for (int n = 0; n < Math.min(20, key.length - offset); n++) key[offset + n] ^= block[n];
        }
        boolean valid = MessageDigest.isEqual(digest(pass, key), Arrays.copyOfRange(bytes, bytes.length - 20, bytes.length));
        Arrays.fill(pass, (byte)0);
        if (!valid) { Arrays.fill(key, (byte)0); throw new UnrecoverableKeyException("Incorrect key password"); }
        return key;
    }
    @Override public Key engineGetKey(String name, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        Entry entry = entries.get(alias(name)); if (entry == null || entry.key == null) return null;
        try {
            byte[] bytes = unprotect(entry.key, password);
            for (String algorithm : new String[]{"RSA", "EC", "DSA", "Ed25519", "Ed448"}) try { return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(bytes)); } catch (GeneralSecurityException ignored) { }
            throw new UnrecoverableKeyException("Unsupported private key algorithm");
        } catch (IOException | GeneralSecurityException e) { UnrecoverableKeyException error = new UnrecoverableKeyException(e.getMessage()); error.initCause(e); throw error; }
    }
    @Override public Certificate[] engineGetCertificateChain(String name) { Entry e = entries.get(alias(name)); return e == null || e.key == null ? null : e.chain.clone(); }
    @Override public Certificate engineGetCertificate(String name) { Entry e = entries.get(alias(name)); return e == null || e.chain == null || e.chain.length == 0 ? null : e.chain[0]; }
    @Override public Date engineGetCreationDate(String name) { Entry e = entries.get(alias(name)); return e == null ? null : new Date(e.time); }
    @Override public void engineSetKeyEntry(String name, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        if (!(key instanceof PrivateKey) || chain == null || chain.length == 0) throw new KeyStoreException("Private key and certificate chain required");
        try { engineSetKeyEntry(name, protect(key.getEncoded(), password), chain); } catch (GeneralSecurityException e) { throw new KeyStoreException(e); }
    }
    @Override public void engineSetKeyEntry(String name, byte[] key, Certificate[] chain) throws KeyStoreException {
        if (chain == null || chain.length == 0) throw new KeyStoreException("Certificate chain required");
        Entry e = new Entry(); e.time = System.currentTimeMillis(); e.key = key.clone(); e.chain = chain.clone(); entries.put(alias(name), e);
    }
    @Override public void engineSetCertificateEntry(String name, Certificate certificate) throws KeyStoreException {
        if (engineIsKeyEntry(name)) throw new KeyStoreException("Alias contains a private key");
        Entry e = new Entry(); e.time = System.currentTimeMillis(); e.chain = new Certificate[]{certificate}; entries.put(alias(name), e);
    }
    @Override public void engineDeleteEntry(String name) { entries.remove(alias(name)); }
    @Override public Enumeration<String> engineAliases() { return Collections.enumeration(entries.keySet()); }
    @Override public boolean engineContainsAlias(String name) { return entries.containsKey(alias(name)); }
    @Override public int engineSize() { return entries.size(); }
    @Override public boolean engineIsKeyEntry(String name) { Entry e = entries.get(alias(name)); return e != null && e.key != null; }
    @Override public boolean engineIsCertificateEntry(String name) { Entry e = entries.get(alias(name)); return e != null && e.key == null; }
    @Override public String engineGetCertificateAlias(Certificate certificate) { for (String name : entries.keySet()) if (certificate.equals(engineGetCertificate(name))) return name; return null; }
    @Override public void engineStore(OutputStream output, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(buffer);
        data.writeInt(MAGIC); data.writeInt(2); data.writeInt(entries.size());
        for (Map.Entry<String, Entry> value : entries.entrySet()) {
            Entry entry = value.getValue(); data.writeInt(entry.key == null ? 2 : 1); data.writeUTF(value.getKey()); data.writeLong(entry.time);
            if (entry.key != null) { data.writeInt(entry.key.length); data.write(entry.key); data.writeInt(entry.chain.length); }
            for (Certificate cert : entry.chain) { byte[] encoded = cert.getEncoded(); data.writeUTF(cert.getType()); data.writeInt(encoded.length); data.write(encoded); }
        }
        data.flush(); byte[] content = buffer.toByteArray(), pass = passwordBytes(password);
        output.write(content); output.write(digest(pass, "Mighty Aphrodite".getBytes(StandardCharsets.UTF_8), content)); output.flush(); Arrays.fill(pass, (byte)0);
    }
    @Override public void engineLoad(InputStream input, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        entries.clear(); if (input == null) return;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] block = new byte[8192];
        for (int n; (n = input.read(block)) >= 0;) { if (buffer.size() + n > MAX_BYTES) throw new IOException("Keystore too large"); if (n > 0) buffer.write(block, 0, n); }
        byte[] bytes = buffer.toByteArray(); if (bytes.length < 32) throw new IOException("Truncated JKS");
        byte[] content = Arrays.copyOf(bytes, bytes.length - 20), pass = passwordBytes(password);
        boolean valid = MessageDigest.isEqual(digest(pass, "Mighty Aphrodite".getBytes(StandardCharsets.UTF_8), content), Arrays.copyOfRange(bytes, bytes.length - 20, bytes.length));
        Arrays.fill(pass, (byte)0); if (!valid) throw new IOException("Incorrect store password or damaged JKS");
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(content));
        if (data.readInt() != MAGIC) throw new IOException("Invalid JKS");
        int version = data.readInt(); if (version != 1 && version != 2) throw new IOException("Unsupported JKS version");
        int count = data.readInt(); if (count < 0 || count > 10000) throw new IOException("Invalid entry count");
        Map<String, Entry> loaded = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int tag = data.readInt(); String name = alias(data.readUTF()); Entry entry = new Entry(); entry.time = data.readLong();
            if (tag == 1) { entry.key = readBytes(data); int chain = data.readInt(); if (chain < 1 || chain > 100) throw new IOException("Invalid certificate chain"); entry.chain = new Certificate[chain]; }
            else if (tag == 2) entry.chain = new Certificate[1]; else throw new IOException("Invalid JKS entry");
            for (int n = 0; n < entry.chain.length; n++) { String type = version == 2 ? data.readUTF() : "X.509"; entry.chain[n] = CertificateFactory.getInstance(type).generateCertificate(new ByteArrayInputStream(readBytes(data))); }
            loaded.put(name, entry);
        }
        if (data.available() != 0) throw new IOException("Trailing JKS content"); entries.putAll(loaded);
    }
    private static byte[] readBytes(DataInputStream data) throws IOException {
        int length = data.readInt(); if (length < 0 || length > MAX_BYTES || length > data.available()) throw new IOException("Invalid JKS field length");
        byte[] value = new byte[length]; data.readFully(value); return value;
    }
}
