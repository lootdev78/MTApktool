package io.github.lootdev78.mtftp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;

/** Protocol paths are slash-separated, independent of local filesystem paths. */
public final class FtpPaths {
    private FtpPaths() {}

    public static String name(String value) throws IOException {
        if (value == null || value.isEmpty() || value.equals(".") || value.equals("..") ||
                value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || hasControl(value))
            throw new IOException("Invalid file name");
        return value;
    }

    public static boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < 32 || value.charAt(i) == 127) return true;
        return false;
    }

    public static String normalize(String value) throws IOException {
        if (value == null || hasControl(value) || value.indexOf('\\') >= 0) throw new IOException("Invalid FTP path");
        ArrayDeque<String> parts = new ArrayDeque<>();
        for (String part : value.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else parts.addLast(name(part));
        }
        return "/" + String.join("/", parts);
    }

    public static String child(String parent, String name) throws IOException {
        return normalize(parent + "/" + name(name));
    }

    public static String parent(String path) throws IOException {
        String normalized = normalize(path);
        int slash = normalized.lastIndexOf('/');
        return slash <= 0 ? "/" : normalized.substring(0, slash);
    }

    public static File confined(File root, String virtualPath) throws IOException {
        File base = root.getCanonicalFile();
        File candidate = new File(base, normalize(virtualPath).substring(1)).getCanonicalFile();
        if (!candidate.equals(base) && !candidate.getPath().startsWith(base.getPath() + File.separator))
            throw new IOException("Path leaves the shared folder");
        return candidate;
    }
}
