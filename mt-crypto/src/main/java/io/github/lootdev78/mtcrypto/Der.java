package io.github.lootdev78.mtcrypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

final class Der {
    static byte[] join(byte[]... values) { ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] v : values) out.write(v, 0, v.length); return out.toByteArray(); }
    static byte[] tag(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(tag);
        if (value.length < 128) out.write(value.length);
        else { int bytes = 0; for (int n = value.length; n != 0; n >>>= 8) bytes++; out.write(0x80 | bytes); for (int n = bytes - 1; n >= 0; n--) out.write(value.length >>> (8 * n)); }
        out.write(value, 0, value.length); return out.toByteArray();
    }
    static byte[] sequence(byte[]... values) { return tag(0x30, join(values)); }
    static byte[] integer(BigInteger value) { return tag(2, value.toByteArray()); }
    static byte[] octets(byte[] value) { return tag(4, value); }
    static byte[] bitString(byte[] value) { return tag(3, join(new byte[] {0}, value)); }
    static byte[] oid(String value) {
        String[] parts = value.split("\\."); ByteArrayOutputStream out = new ByteArrayOutputStream();
        base128(out, Long.parseLong(parts[0]) * 40 + Long.parseLong(parts[1]));
        for (int i = 2; i < parts.length; i++) base128(out, Long.parseLong(parts[i]));
        return tag(6, out.toByteArray());
    }
    private static void base128(ByteArrayOutputStream out, long value) {
        byte[] bytes = new byte[10]; int index = bytes.length; bytes[--index] = (byte)(value & 127);
        while ((value >>>= 7) != 0) bytes[--index] = (byte)((value & 127) | 128);
        out.write(bytes, index, bytes.length - index);
    }
    static final class Reader {
        final byte[] bytes; int position;
        Reader(byte[] bytes) { this.bytes = bytes; }
        byte[] read(int expected) throws IOException {
            if (position >= bytes.length || (bytes[position++] & 255) != expected || position >= bytes.length) throw new IOException("Invalid DER tag");
            int length = bytes[position++] & 255;
            if ((length & 128) != 0) {
                int count = length & 127; length = 0;
                if (count == 0 || count > 4 || position + count > bytes.length) throw new IOException("Invalid DER length");
                for (int i = 0; i < count; i++) length = (length << 8) | (bytes[position++] & 255);
            }
            if (length < 0 || length > bytes.length - position) throw new IOException("DER length out of bounds");
            byte[] value = java.util.Arrays.copyOfRange(bytes, position, position + length); position += length; return value;
        }
    }
}
