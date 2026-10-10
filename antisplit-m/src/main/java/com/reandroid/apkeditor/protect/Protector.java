package com.reandroid.apkeditor.protect;

import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import java.io.File;
import java.io.IOException;

/** Host adapter: no APKEditor CLI, decompiler or cloner is introduced. */
public final class Protector implements APKLogger {
    private final ProtectorOptions options;
    private final APKLogger logger;
    private final ApkModule module;
    public Protector(ProtectorOptions options, APKLogger logger, ApkModule module) {
        this.options = options; this.logger = logger; this.module = module;
    }
    public ProtectorOptions getOptions() { return options; }
    public ApkModule getApkModule() { return module; }
    public void write(File output) throws IOException {
        if (!module.hasTableBlock()) throw new IOException("Die APK hat keine resources.arsc für den REAndroid-Schutz.");
        module.setLoadDefaultFramework(false);
        new ManifestConfuser(this).confuse();
        new DirectoryConfuser(this).confuse();
        new FileNameConfuser(this).confuse();
        new TableConfuser(this).confuse();
        new DexConfuser(this).confuse();
        module.getTableBlock().refresh();
        logMessage("APK schreiben");
        if (options.confuse_zip) new ProtectedFileWriter(module, output).write();
        else module.writeApk(output);
    }
    @Override public void logMessage(String text) { logger.logMessage("[PROTECT] " + text); }
    @Override public void logVerbose(String text) { logger.logVerbose("[PROTECT] " + text); }
    @Override public void logError(String text, Throwable error) { logger.logError(text, error); }
}
