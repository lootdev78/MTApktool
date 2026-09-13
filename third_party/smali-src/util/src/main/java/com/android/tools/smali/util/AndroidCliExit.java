package com.android.tools.smali.util;

/** Android-safe replacement for CLI System.exit calls in the vendored smali/baksmali sources. */
public final class AndroidCliExit extends RuntimeException {
    private final int exitCode;

    private AndroidCliExit(int exitCode) {
        super("smali/baksmali CLI exit " + exitCode, null, false, false);
        this.exitCode = exitCode;
    }

    /** Kept as a void method so upstream command control flow remains source-compatible. */
    public static void exit(int exitCode) {
        throw new AndroidCliExit(exitCode);
    }

    public int getExitCode() {
        return exitCode;
    }
}
