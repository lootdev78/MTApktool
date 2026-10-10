package io.github.lootdev78.mtftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.TrustManagerUtils;
import java.io.*;
import java.util.*;

/** One serialized connection. No Android dependencies and no stored credentials. */
public final class MtFtpClient implements Closeable {
    public enum Security { FTP, EXPLICIT_TLS, IMPLICIT_TLS }
    public interface Progress { void bytes(long completed) throws IOException; }
    public static final class Entry {
        public final String name, path;
        public final boolean directory;
        public final long size, modified;
        Entry(String name, String path, boolean directory, long size, long modified) {
            this.name = name; this.path = path; this.directory = directory; this.size = size; this.modified = modified;
        }
    }
    private volatile FTPClient client;

    public synchronized String connect(String host, int port, String username, String password, Security security, String directory) throws IOException {
        close();
        if (host == null || host.trim().isEmpty() || FtpPaths.hasControl(host) || host.contains("/") || port < 1 || port > 65535)
            throw new IOException("Invalid host or port");
        if (FtpPaths.hasControl(username) || FtpPaths.hasControl(password)) throw new IOException("Invalid credentials");
        FTPClient ftp;
        if (security == Security.FTP) ftp = new FTPClient();
        else {
            FTPSClient tls = new FTPSClient("TLS", security == Security.IMPLICIT_TLS);
            try { tls.setTrustManager(TrustManagerUtils.getDefaultTrustManager(null)); }
            catch (java.security.GeneralSecurityException e) { throw new IOException("TLS trust store unavailable", e); }
            tls.setEndpointCheckingEnabled(true);
            ftp = tls;
        }
        client = ftp;
        try {
            ftp.setConnectTimeout(15000);
            ftp.setDefaultTimeout(30000);
            ftp.setDataTimeout(30000);
            ftp.setControlEncoding("UTF-8");
            ftp.setAutodetectUTF8(true);
            ftp.setBufferSize(128 * 1024);
            ftp.setIpAddressFromPasvResponse(false);
            ftp.connect(host.trim(), port);
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) throw failure("Connection rejected");
            if (!ftp.login(username, password)) throw failure("Login failed");
            if (ftp instanceof FTPSClient) { ((FTPSClient)ftp).execPBSZ(0); ((FTPSClient)ftp).execPROT("P"); }
            ftp.enterLocalPassiveMode();
            if (!ftp.setFileType(FTP.BINARY_FILE_TYPE)) throw failure("Binary mode rejected");
            ftp.setListHiddenFiles(true);
            if (!ftp.changeWorkingDirectory(FtpPaths.normalize(directory))) throw failure("Directory unavailable");
            String pwd = ftp.printWorkingDirectory();
            if (pwd == null) throw failure("Cannot read working directory");
            return FtpPaths.normalize(pwd);
        } catch (IOException | RuntimeException e) { abortConnection(); throw e; }
    }

    private FTPClient connection() throws IOException {
        FTPClient ftp = client;
        if (ftp == null || !ftp.isConnected()) throw new IOException("FTP connection closed; reconnect the pane");
        return ftp;
    }
    private IOException failure(String operation) {
        FTPClient ftp = client;
        return new IOException(operation + (ftp == null ? "" : " (FTP " + ftp.getReplyCode() + ")"));
    }
    public synchronized Integer permissions(String path) throws IOException {
        String normalized = FtpPaths.normalize(path);
        FTPClient ftp = connection();
        if (ftp.sendCommand("SITE", "MODE " + normalized) == 200) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\bMODE ([0-7]{3,4})\\b").matcher(ftp.getReplyString());
            if (matcher.find()) return Integer.parseInt(matcher.group(1), 8);
        }
        FTPFile[] files = connection().listFiles(FtpPaths.parent(normalized));
        if (!FTPReply.isPositiveCompletion(connection().getReplyCode())) throw failure("Cannot read permissions");
        if (files != null) for (FTPFile file : files) {
            try { FtpPaths.name(file.getName()); } catch (IOException ignored) { continue; }
            if (!FtpPaths.child(FtpPaths.parent(normalized), file.getName()).equals(normalized)) continue;
            String listing = file.getRawListing();
            if (listing == null || !listing.matches("^[dl-][rwxstST-]{9}.*")) return null;
            int mode = 0;
            for (int i = 0; i < 9; i++) {
                char value = listing.charAt(i + 1);
                if (value != '-' && value != 'S' && value != 'T') mode |= 1 << (8 - i);
            }
            if (listing.charAt(3) == 's' || listing.charAt(3) == 'S') mode |= 04000;
            if (listing.charAt(6) == 's' || listing.charAt(6) == 'S') mode |= 02000;
            if (listing.charAt(9) == 't' || listing.charAt(9) == 'T') mode |= 01000;
            return mode;
        }
        throw new IOException("Remote file no longer exists");
    }
    public synchronized void chmod(String path, int mode) throws IOException {
        if (mode < 0 || mode > 07777) throw new IOException("Invalid Unix mode");
        String normalized = FtpPaths.normalize(path);
        if (!connection().sendSiteCommand("CHMOD " + Integer.toOctalString(mode) + " " + normalized)) throw failure("Server rejected SITE CHMOD");
    }
    public synchronized List<Entry> list(String path) throws IOException {
        FTPClient ftp = connection();
        String normalized = FtpPaths.normalize(path);
        FTPFile[] machineFiles = ftp.mlistDir(normalized);
        boolean machineOk = machineFiles != null && FTPReply.isPositiveCompletion(ftp.getReplyCode());
        // Some servers omit dot files from MLSD. LIST -a includes unfinished uploads and backups,
        // which must participate in recursive copy/delete rather than being silently abandoned.
        FTPFile[] listedFiles = ftp.listFiles(normalized);
        boolean listOk = listedFiles != null && FTPReply.isPositiveCompletion(ftp.getReplyCode());
        if (!machineOk && !listOk) throw failure("Cannot list directory");
        java.util.LinkedHashMap<String, FTPFile> all = new java.util.LinkedHashMap<>();
        if (machineOk) for (FTPFile file : machineFiles) all.put(file.getName(), file);
        if (listOk) for (FTPFile file : listedFiles) all.putIfAbsent(file.getName(), file);
        FTPFile[] files = all.values().toArray(new FTPFile[0]);
        List<Entry> result = new ArrayList<>();
        if (files != null) for (FTPFile file : files) {
            if (file.isSymbolicLink() || (!file.isFile() && !file.isDirectory())) continue;
            String name = file.getName();
            try { FtpPaths.name(name); } catch (IOException ignored) { continue; }
            result.add(new Entry(name, FtpPaths.child(normalized, name), file.isDirectory(), file.getSize(),
                    file.getTimestamp() == null ? 0 : file.getTimestamp().getTimeInMillis()));
        }
        return result;
    }
    public synchronized Entry stat(String path) throws IOException {
        String normalized = FtpPaths.normalize(path);
        if (normalized.equals("/")) return new Entry("/", "/", true, 0, 0);
        for (Entry entry : list(FtpPaths.parent(normalized))) if (entry.path.equals(normalized)) return entry;
        return null;
    }
    public synchronized void mkdir(String path) throws IOException {
        if (!connection().makeDirectory(FtpPaths.normalize(path))) throw failure("Cannot create directory");
    }
    public synchronized void rename(String from, String to) throws IOException {
        String source = FtpPaths.normalize(from), target = FtpPaths.normalize(to);
        if (source.equals("/") || source.equals(target) || target.startsWith(source + "/")) throw new IOException("Invalid rename target");
        if (stat(target) != null) throw new IOException("Destination already exists");
        if (!connection().rename(source, target)) throw failure("Rename failed");
    }
    public synchronized void delete(String path) throws IOException {
        String normalized = FtpPaths.normalize(path);
        if (normalized.equals("/")) throw new IOException("Cannot delete FTP root");
        Entry entry = stat(normalized);
        if (entry == null) throw new IOException("Source no longer exists");
        if (entry.directory) {
            for (Entry child : list(normalized)) delete(child.path);
            if (!connection().removeDirectory(normalized)) throw failure("Cannot delete directory");
        } else if (!connection().deleteFile(normalized)) throw failure("Cannot delete file");
    }

    public synchronized void download(String path, OutputStream destination, Progress progress) throws IOException {
        FTPClient ftp = connection();
        try {
            InputStream stream = ftp.retrieveFileStream(FtpPaths.normalize(path));
            if (stream == null) throw failure("Download rejected");
            try (InputStream input = stream) { copy(input, destination, progress); }
            if (!ftp.completePendingCommand()) throw failure("Download incomplete");
        } catch (IOException | RuntimeException e) {
            // A failed data command must not leave a queued 226 response for the next operation.
            abortConnection(); throw e;
        }
    }

    public synchronized void upload(String path, InputStream source, boolean replace, Progress progress) throws IOException {
        FTPClient ftp = connection();
        String target = FtpPaths.normalize(path);
        String temporary = FtpPaths.child(FtpPaths.parent(target), ".mt-upload-" + UUID.randomUUID());
        String backup = FtpPaths.child(FtpPaths.parent(target), ".mt-backup-" + UUID.randomUUID());
        boolean backedUp = false;
        try {
            OutputStream stream = ftp.storeFileStream(temporary);
            if (stream == null) throw failure("Upload rejected");
            try (OutputStream output = stream) { copy(source, output, progress); }
            if (!ftp.completePendingCommand()) throw failure("Upload incomplete");
            Entry existing = stat(target);
            if (existing != null) {
                if (!replace || existing.directory) throw new IOException("Destination already exists");
                if (!ftp.rename(target, backup)) throw failure("Cannot preserve existing destination");
                backedUp = true;
            }
            if (!ftp.rename(temporary, target)) throw failure("Cannot finish upload");
            if (backedUp && !ftp.deleteFile(backup)) throw failure("Upload saved; old backup could not be removed");
        } catch (IOException | RuntimeException e) {
            if (backedUp) try { ftp.rename(backup, target); } catch (IOException ignored) { }
            // Closing the control connection also closes any still-active data command.
            abortConnection(); throw e;
        } finally {
            // Best effort only: never remove a successfully published target or its recovery backup.
            if (ftp.isConnected()) try { ftp.deleteFile(temporary); } catch (IOException ignored) { }
        }
    }
    private static void copy(InputStream source, OutputStream destination, Progress progress) throws IOException {
        byte[] buffer = new byte[128 * 1024]; long completed = 0;
        progress.bytes(0);
        for (int count; (count = source.read(buffer)) != -1;) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Transfer cancelled");
            if (count == 0) continue;
            destination.write(buffer, 0, count); completed += count; progress.bytes(completed);
        }
        destination.flush();
    }
    /** May be called from another thread to cancel a blocked transfer. */
    public void abortConnection() {
        FTPClient ftp = client;
        if (ftp != null) try { ftp.disconnect(); } catch (IOException ignored) { }
    }
    @Override public synchronized void close() {
        FTPClient ftp = client;
        if (ftp != null && ftp.isConnected()) try { ftp.logout(); } catch (IOException ignored) { }
        abortConnection(); client = null;
    }
}
