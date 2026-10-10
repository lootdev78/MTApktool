package io.github.lootdev78.mtapktool.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.zip.CRC32;

/** Reads every byte without extraction and checks the advertised size/checksum when present. */
public final class ArchiveReadVerifier {
    private ArchiveReadVerifier() {}

    public static long read(InputStream input, long expectedSize, long expectedCrc, Runnable checkpoint)
            throws IOException {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[65536];
        long total = 0;
        while (true) {
            checkpoint.run();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Archivtest abgebrochen");
            int count = input.read(buffer);
            if (count < 0) break;
            if (count == 0) continue;
            if (Long.MAX_VALUE - total < count) throw new IOException("Archiv-Eintrag ist zu groß");
            total += count;
            crc.update(buffer, 0, count);
            if (expectedSize >= 0 && total > expectedSize) throw new IOException("Archiv-Eintrag ist größer als angegeben");
        }
        if (expectedSize >= 0 && total != expectedSize) throw new IOException("Archiv-Eintrag ist abgeschnitten");
        if (expectedCrc >= 0 && crc.getValue() != (expectedCrc & 0xffffffffL)) {
            throw new IOException("CRC-Prüfsumme stimmt nicht");
        }
        return total;
    }
}
