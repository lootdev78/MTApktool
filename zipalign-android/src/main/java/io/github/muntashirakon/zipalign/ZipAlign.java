package io.github.muntashirakon.zipalign;

import com.iyxan23.zipalignjava.InvalidZipException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Android zipalign facade.
 *
 * <p>The default implementation vendors zipalign-java 1.2.2 so on-device Android
 * IDEs do not need CMake/NDK for normal builds. Optional JNI remains available
 * with {@code -Papktool.nativeZipalign=true}.</p>
 */
public final class ZipAlign {
    private static final boolean HAS_NATIVE = loadNative();

    private ZipAlign() {}

    private static boolean loadNative() {
        try {
            System.loadLibrary("zipalign");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static native boolean doZipAlignNative(
            String inZipFile,
            String outZipFile,
            int alignment,
            int sharedLibraryPageAlignment,
            boolean force);

    private static native boolean isZipAlignedNative(
            String zipFile,
            int alignment,
            int sharedLibraryPageAlignment);

    /**
     * @param alignment normal uncompressed-entry alignment in bytes, usually 4
     * @param sharedLibraryPageAlignment shared-library alignment in bytes; 0 disables special .so alignment.
     */
    public static boolean doZipAlign(
            String inZipFile,
            String outZipFile,
            int alignment,
            int sharedLibraryPageAlignment,
            boolean force) {
        if (HAS_NATIVE) {
            try {
                return doZipAlignNative(inZipFile, outZipFile, alignment, sharedLibraryPageAlignment, force);
            } catch (Throwable ignored) {
                // Fall through to vendored zipalign-java.
            }
        }
        try {
            File input = new File(inZipFile);
            File output = new File(outZipFile);
            doJavaZipAlign(input, output, alignment, sharedLibraryPageAlignment, force);
            return ZipAlignmentVerifier.verify(output, alignment, sharedLibraryPageAlignment);
        } catch (Exception e) {
            throw new RuntimeException("zipalign failed", e);
        }
    }

    /** Verify archive alignment using the same rules as {@link #doZipAlign}. */
    public static boolean isZipAligned(
            String zipFile,
            int alignment,
            int sharedLibraryPageAlignment) {
        if (HAS_NATIVE) {
            try {
                return isZipAlignedNative(zipFile, alignment, sharedLibraryPageAlignment);
            } catch (Throwable ignored) {
                // Fall through to Java verifier.
            }
        }
        try {
            return ZipAlignmentVerifier.verify(new File(zipFile), alignment, sharedLibraryPageAlignment);
        } catch (Exception e) {
            throw new RuntimeException("zipalign verification failed", e);
        }
    }

    private static void doJavaZipAlign(
            File input,
            File output,
            int alignment,
            int sharedLibraryPageAlignment,
            boolean force) throws IOException, InvalidZipException {
        validateAlignment(alignment);
        if (sharedLibraryPageAlignment != 0) validateAlignment(sharedLibraryPageAlignment);
        if (!input.isFile()) throw new IOException("Input archive not found: " + input);

        File realInput = input.getCanonicalFile();
        File realOutput = output.getCanonicalFile();
        if (realOutput.exists() && !force) throw new IOException("Output already exists: " + output);

        File parent = realOutput.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create output directory: " + parent);
        }

        boolean sameFile = realInput.equals(realOutput);
        File writeTarget = sameFile
                ? File.createTempFile("zipalign-java-", ".apk", parent != null ? parent : realInput.getParentFile())
                : realOutput;

        boolean success = false;
        try (RandomAccessFile zipIn = new RandomAccessFile(realInput, "r");
             FileOutputStream zipOut = new FileOutputStream(writeTarget)) {
            com.iyxan23.zipalignjava.ZipAlign.alignZip(
                    zipIn,
                    zipOut,
                    alignment,
                    sharedLibraryPageAlignment);
            success = true;
        } finally {
            if (!success && !writeTarget.equals(realOutput)) {
                //noinspection ResultOfMethodCallIgnored
                writeTarget.delete();
            }
        }

        if (sameFile) {
            replaceFile(writeTarget, realOutput);
        }
    }

    private static void replaceFile(File source, File target) throws IOException {
        if (!source.renameTo(target)) {
            try (FileInputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            //noinspection ResultOfMethodCallIgnored
            source.delete();
        }
    }

    private static void validateAlignment(int alignment) {
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("Alignment must be a positive power of two: " + alignment);
        }
    }
}
