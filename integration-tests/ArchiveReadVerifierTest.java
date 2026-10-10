import io.github.lootdev78.mtapktool.archive.ArchiveReadVerifier;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

/** Checks the production full-stream verifier with real ZIP/GZIP streams and failures at EOF. */
public final class ArchiveReadVerifierTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private interface Operation { void run() throws Exception; }
    private static void fails(Operation operation, Class<? extends Exception> type, String message) throws Exception {
        try { operation.run(); throw new AssertionError(message); }
        catch (Exception error) { check(type.isInstance(error), message + ": " + error); }
    }
    private static long crc(byte[] bytes) { CRC32 crc = new CRC32(); crc.update(bytes); return crc.getValue(); }
    private static long read(byte[] bytes, long size, long checksum) throws Exception {
        return ArchiveReadVerifier.read(new ByteArrayInputStream(bytes), size, checksum, () -> {});
    }
    public static void main(String[] args) throws Exception {
        byte[] payload = new byte[512 * 1024 + 17];
        new Random(1401).nextBytes(payload);
        check(read(new byte[0], 0, 0) == 0, "Empty entry");
        check(read(payload, payload.length, crc(payload)) == payload.length, "All bytes across buffer boundaries");
        check(read(payload, -1, -1) == payload.length, "Unknown size/checksum still reads to EOF");
        check(read(payload, -1, crc(payload)) == payload.length, "CRC without advertised size");
        byte[] changed = payload.clone(); changed[changed.length - 1] ^= 1;
        fails(() -> read(changed, changed.length, crc(payload)), IOException.class, "Corruption in final byte detected");
        fails(() -> read(Arrays.copyOf(payload, payload.length - 1), payload.length, crc(payload)), IOException.class, "Truncated payload detected");
        fails(() -> read(payload, payload.length - 1, crc(payload)), IOException.class, "Oversized payload detected");
        InputStream shortReads = new ByteArrayInputStream(payload) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) { return super.read(buffer, offset, Math.min(length, 7)); }
        };
        check(ArchiveReadVerifier.read(shortReads, payload.length, crc(payload), () -> {}) == payload.length, "Partial stream reads");
        InputStream sometimesZero = new ByteArrayInputStream(payload) {
            int calls;
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                if (++calls % 3 == 0) return 0;
                return super.read(buffer, offset, length);
            }
        };
        check(ArchiveReadVerifier.read(sometimesZero, payload.length, crc(payload), () -> {}) == payload.length, "Zero-length reads do not end verification");
        InputStream broken = new ByteArrayInputStream(payload) {
            int calls;
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                if (++calls > 1) throw new UncheckedIOException(new IOException("Storage failed"));
                return super.read(buffer, offset, length);
            }
        };
        fails(() -> ArchiveReadVerifier.read(broken, -1, -1, () -> {}), UncheckedIOException.class, "Read failure is propagated");
        AtomicInteger checkpoints = new AtomicInteger();
        ByteArrayInputStream cancelled = new ByteArrayInputStream(payload);
        fails(() -> ArchiveReadVerifier.read(cancelled, -1, -1, () -> {
            if (checkpoints.incrementAndGet() >= 3) throw new CancellationException("Cancelled");
        }), CancellationException.class, "Cooperative cancellation");
        check(cancelled.available() > 0, "Cancellation stops before EOF");
        Thread.currentThread().interrupt();
        try {
            fails(() -> read(payload, -1, -1), InterruptedIOException.class, "Thread interruption");
            check(Thread.currentThread().isInterrupted(), "Interrupt flag retained");
        } finally { Thread.interrupted(); }

        Path work = Files.createTempDirectory("mt-archive-verifier-");
        try {
            Path archive = work.resolve("valid.zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                ZipEntry directory = new ZipEntry("docs/"); zip.putNextEntry(directory); zip.closeEntry();
                ZipEntry entry = new ZipEntry("docs/Größe.txt"); zip.putNextEntry(entry); zip.write(payload); zip.closeEntry();
                ZipEntry stored = new ZipEntry("raw.bin"); stored.setMethod(ZipEntry.STORED);
                stored.setSize(payload.length); stored.setCrc(crc(payload));
                zip.putNextEntry(stored); zip.write(payload); zip.closeEntry();
            }
            byte[] original = Files.readAllBytes(archive);
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                check(zip.size() == 3, "ZIP directory and both file entries");
                for (String name : List.of("docs/Größe.txt", "raw.bin")) {
                    ZipEntry entry = zip.getEntry(name);
                    try (InputStream input = zip.getInputStream(entry)) {
                        check(ArchiveReadVerifier.read(input, entry.getSize(), entry.getCrc(), () -> {}) == payload.length, "Real ZIP entry fully verified: " + name);
                    }
                }
            }
            check(Arrays.equals(original, Files.readAllBytes(archive)), "Archive remains unchanged");
            int offset = -1;
            outer: for (int i = 0; i <= original.length - payload.length; i++) {
                if (original[i] != payload[0]) continue;
                for (int n = 1; n < payload.length; n++) if (original[i + n] != payload[n]) continue outer;
                offset = i; break;
            }
            check(offset >= 0, "Stored payload located");
            byte[] corrupt = original.clone(); corrupt[offset + payload.length - 1] ^= 1;
            Path bad = work.resolve("bad.zip"); Files.write(bad, corrupt);
            try (ZipFile zip = new ZipFile(bad.toFile())) {
                ZipEntry entry = zip.getEntry("raw.bin");
                try (InputStream input = zip.getInputStream(entry)) {
                    fails(() -> ArchiveReadVerifier.read(input, entry.getSize(), entry.getCrc(), () -> {}), IOException.class, "Last-byte corruption in a real ZIP");
                }
            }
            Charset cp437 = Charset.forName("IBM437");
            Path legacy = work.resolve("legacy.zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(legacy), cp437)) {
                zip.putNextEntry(new ZipEntry("Größe.txt")); zip.write(payload); zip.closeEntry();
            }
            try (ZipFile zip = new ZipFile(legacy.toFile(), cp437)) {
                ZipEntry entry = zip.getEntry("Größe.txt");
                check(entry != null, "Legacy CP437 filename");
                try (InputStream input = zip.getInputStream(entry)) {
                    check(ArchiveReadVerifier.read(input, entry.getSize(), entry.getCrc(), () -> {}) == payload.length, "Legacy ZIP stream verification");
                }
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) { gzip.write(payload); }
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
                check(ArchiveReadVerifier.read(gzip, -1, -1, () -> {}) == payload.length, "GZIP trailer consumed");
            }
            byte[] badGzip = compressed.toByteArray(); badGzip[badGzip.length - 8] ^= 1;
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(badGzip))) {
                fails(() -> ArchiveReadVerifier.read(gzip, -1, -1, () -> {}), IOException.class, "GZIP trailer corruption detected");
            }
        } finally {
            try (var files = Files.walk(work)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS archive stream verifier: " + assertions + " assertions");
    }
}
