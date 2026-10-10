package io.github.abdurazaaqmohammed.apksigner;

import com.android.apksig.ApkSigner;

import java.io.File;
import java.security.KeyStore;
import io.github.lootdev78.mtcrypto.KeyStoreTools;
import java.util.ArrayList;
import java.util.List;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class SignWrapper {

    public void setKey(File key) {
        this.key = key;
    }

    File key;
    char[] pw;
    char[] keyPassword;
    String alias;
    boolean v1;
    boolean v2;
    boolean v3;
    boolean v4;
    public SignWrapper(String signPath, String password, boolean v1Enabled, boolean v2Enabled, boolean v3Enabled, boolean v4Enabled) {
        this(signPath, password, "", password, v1Enabled, v2Enabled, v3Enabled, v4Enabled);
    }
    public SignWrapper(String signPath, String password, String alias, String keyPassword, boolean v1Enabled, boolean v2Enabled, boolean v3Enabled, boolean v4Enabled) {
        this.key = new File(signPath); this.pw = password.toCharArray(); this.alias = alias; this.keyPassword = keyPassword.toCharArray();
        this.v1 = v1Enabled; this.v2 = v2Enabled; this.v3 = v3Enabled; this.v4 = v4Enabled;
    }

    public SignWrapper(String signPath, String password) {
        this(signPath, password, true, true, true, false);
    }

    public void signApk(File inputApk, File output, boolean v1, boolean v2, boolean v3, boolean v4) throws Exception {
        if (inputApk.getCanonicalFile().equals(output.getCanonicalFile())) throw new java.io.IOException("Input and output must differ");
        KeyStore keystore = KeyStoreTools.open(key, pw);
        KeyStore.PrivateKeyEntry entry = KeyStoreTools.keyEntry(keystore, alias, keyPassword);
        List<X509Certificate> certificates = new ArrayList<>();
        for (java.security.cert.Certificate certificate : entry.getCertificateChain()) certificates.add((X509Certificate) certificate);
        ApkSigner.Builder b = new ApkSigner.Builder(Collections.singletonList(new ApkSigner.SignerConfig.Builder("CERT",
                entry.getPrivateKey(), certificates).build()))
                .setInputApk(inputApk)
                .setOutputApk(output)
                .setCreatedBy("MTApktool")
                .setV1SigningEnabled(v1)
                .setV2SigningEnabled(v2)
                .setV3SigningEnabled(v3)
                .setV4SigningEnabled(v4);
                if (v4) {
                    // apksigner's detached v4 signature belongs to the output APK,
                    // not the input name. Keep the conventional <output>.idsig name.
                    b.setV4SignatureOutputFile(new File(output.getAbsolutePath() + ".idsig"));
                }
                b.build().sign();
    }

    public void signApk(File inputApk, File output) throws Exception {
        signApk(inputApk, output, v1, v2, v3, v4);
    }
}
