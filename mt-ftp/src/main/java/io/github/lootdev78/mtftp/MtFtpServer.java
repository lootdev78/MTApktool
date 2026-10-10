package io.github.lootdev78.mtftp;

import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.*;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.nio.file.Files;
import org.apache.ftpserver.command.CommandFactoryFactory;

/** Apache FTP protocol with a canonical-path confined, in-memory authenticated filesystem. */
public final class MtFtpServer implements Closeable {
    private FtpServer server;
    public synchronized void start(File directory, int port, String username, String password, boolean writable) throws IOException {
        if (server != null) throw new IOException("FTP server already running");
        File root = directory.getCanonicalFile();
        if (!root.isDirectory() || !root.canRead()) throw new IOException("Shared folder is not readable");
        if (port < 1024 || port > 65535) throw new IOException("Choose a port between 1024 and 65535");
        if (username == null || username.isEmpty() || password == null || password.isEmpty() ||
                FtpPaths.hasControl(username) || FtpPaths.hasControl(password)) throw new IOException("Username and password required");
        BaseUser user = new BaseUser();
        user.setName(username); user.setPassword(password); user.setHomeDirectory(root.getPath());
        user.setEnabled(true); user.setMaxIdleTime(300);
        List<Authority> authorities = new ArrayList<>();
        if (writable) authorities.add(new WritePermission());
        authorities.add(new ConcurrentLoginPermission(4, 4));
        user.setAuthorities(authorities);
        FtpServerFactory factory = new FtpServerFactory();
        factory.setUserManager(new MemoryUsers(user));
        factory.setFileSystem((User ignored) -> new RootedView(root, username, writable));
        CommandFactoryFactory commands = new CommandFactoryFactory();
        commands.addCommand("SITE_MODE", (session, ignored, request) -> {
            if (session.getUser() == null) { session.write(new DefaultFtpReply(530, "Authentication required")); return; }
            String[] parts = Objects.toString(request.getArgument(), "").split(" ", 2);
            if (parts.length != 2) { session.write(new DefaultFtpReply(501, "SITE MODE path")); return; }
            try {
                Object physical = session.getFileSystemView().getFile(parts[1]).getPhysicalFile();
                if (!(physical instanceof File)) throw new IOException("File unavailable");
                int mode = ((Number)Files.getAttribute(((File)physical).toPath(), "unix:mode")).intValue() & 07777;
                session.write(new DefaultFtpReply(200, "MODE " + String.format(Locale.ROOT, "%04o", mode)));
            } catch (Exception error) { session.write(new DefaultFtpReply(550, "Unix permissions unavailable")); }
        });
        commands.addCommand("SITE_CHMOD", (session, ignored, request) -> {
            if (session.getUser() == null) { session.write(new DefaultFtpReply(530, "Authentication required")); return; }
            if (!writable) { session.write(new DefaultFtpReply(550, "Read-only server")); return; }
            String[] parts = Objects.toString(request.getArgument(), "").split(" ", 3);
            if (parts.length != 3 || !parts[1].matches("[0-7]{1,4}")) { session.write(new DefaultFtpReply(501, "SITE CHMOD mode path")); return; }
            try {
                Object physical = session.getFileSystemView().getFile(parts[2]).getPhysicalFile();
                if (!(physical instanceof File)) throw new IOException("File unavailable");
                File file = ((File)physical).getCanonicalFile();
                if (file.equals(root) || !file.toPath().startsWith(root.toPath())) throw new IOException("Shared root permissions are protected");
                int mode = Integer.parseInt(parts[1], 8);
                Files.setAttribute(file.toPath(), "unix:mode", mode);
                if ((((Number)Files.getAttribute(file.toPath(), "unix:mode")).intValue() & 07777) != mode) throw new IOException("Filesystem ignores Unix modes");
                session.write(new DefaultFtpReply(200, "Permissions updated"));
            } catch (Exception error) { session.write(new DefaultFtpReply(550, "Filesystem rejected permission change")); }
        });
        factory.setCommandFactory(commands.createCommandFactory());
        ConnectionConfigFactory connections = new ConnectionConfigFactory();
        connections.setAnonymousLoginEnabled(false); connections.setMaxLogins(4);
        connections.setMaxLoginFailures(3); connections.setLoginFailureDelay(1000);
        factory.setConnectionConfig(connections.createConnectionConfig());
        ListenerFactory listener = new ListenerFactory();
        listener.setPort(port); listener.setIdleTimeout(300);
        factory.addListener("default", listener.createListener());
        FtpServer candidate = factory.createServer();
        try { candidate.start(); server = candidate; }
        catch (Exception e) { candidate.stop(); throw new IOException("FTP server could not start", e); }
    }
    @Override public synchronized void close() { if (server != null) { server.stop(); server = null; } }

    private static final class MemoryUsers implements UserManager {
        private final User user;
        MemoryUsers(User user) { this.user = user; }
        public User authenticate(Authentication authentication) throws AuthenticationFailedException {
            if (authentication instanceof UsernamePasswordAuthentication) {
                UsernamePasswordAuthentication auth = (UsernamePasswordAuthentication) authentication;
                if (user.getName().equals(auth.getUsername()) && auth.getPassword() != null && MessageDigest.isEqual(
                        user.getPassword().getBytes(StandardCharsets.UTF_8), auth.getPassword().getBytes(StandardCharsets.UTF_8))) return user;
            }
            throw new AuthenticationFailedException("Invalid credentials");
        }
        public User getUserByName(String name) { return doesExist(name) ? user : null; }
        public String[] getAllUserNames() { return new String[] {user.getName()}; }
        public boolean doesExist(String name) { return user.getName().equals(name); }
        public String getAdminName() { return user.getName(); }
        public boolean isAdmin(String name) { return false; }
        public void save(User value) throws FtpException { throw new FtpException("Users are configured in memory"); }
        public void delete(String name) throws FtpException { throw new FtpException("Users are configured in memory"); }
    }
    private static final class RootedView implements FileSystemView {
        private final File root; private final String username; private final boolean writable;
        private String current = "/";
        RootedView(File root, String username, boolean writable) { this.root = root; this.username = username; this.writable = writable; }
        public FtpFile getHomeDirectory() { return new RootedFile(root, "/", username, writable); }
        public FtpFile getWorkingDirectory() { return new RootedFile(root, current, username, writable); }
        public FtpFile getFile(String path) throws FtpException {
            try {
                String absolute = FtpPaths.normalize(path.startsWith("/") ? path : current + "/" + path);
                FtpPaths.confined(root, absolute);
                return new RootedFile(root, absolute, username, writable);
            } catch (IOException e) { throw new FtpException("Path is outside the shared folder", e); }
        }
        public boolean changeWorkingDirectory(String path) throws FtpException {
            FtpFile file = getFile(path);
            if (!file.isDirectory() || !file.isReadable()) return false;
            current = file.getAbsolutePath(); return true;
        }
        public boolean isRandomAccessible() { return true; }
        public void dispose() {}
    }
    private static final class RootedFile implements FtpFile {
        private final File root; private final String path, username; private final boolean writable;
        RootedFile(File root, String path, String username, boolean writable) { this.root = root; this.path = path; this.username = username; this.writable = writable; }
        private File checked() throws IOException { return FtpPaths.confined(root, path); }
        private File safe() { try { return checked(); } catch (IOException e) { return null; } }
        public String getAbsolutePath() { return path; }
        public String getName() { return path.equals("/") ? "/" : path.substring(path.lastIndexOf('/') + 1); }
        public boolean isHidden() { return getName().startsWith("."); }
        public boolean doesExist() { File f = safe(); return f != null && f.exists(); }
        public boolean isDirectory() { File f = safe(); return f != null && f.isDirectory(); }
        public boolean isFile() { File f = safe(); return f != null && f.isFile(); }
        public boolean isReadable() { File f = safe(); return f != null && f.canRead(); }
        public boolean isWritable() {
            File f = safe(); return writable && f != null && (f.exists() ? f.canWrite() : f.getParentFile() != null && f.getParentFile().canWrite());
        }
        public boolean isRemovable() { return !path.equals("/") && isWritable(); }
        public long getSize() { File f = safe(); return f == null || f.isDirectory() ? 0 : f.length(); }
        public long getLastModified() { File f = safe(); return f == null ? 0 : f.lastModified(); }
        public boolean setLastModified(long time) { File f = safe(); return isWritable() && f != null && f.setLastModified(time); }
        public String getOwnerName() { return username; }
        public String getGroupName() { return username; }
        public int getLinkCount() { return isDirectory() ? 2 : 1; }
        public Object getPhysicalFile() { return safe(); }
        public boolean mkdir() { File f = safe(); return !path.equals("/") && isWritable() && f != null && f.mkdir(); }
        public boolean delete() { File f = safe(); return isRemovable() && f != null && f.delete(); }
        public boolean move(FtpFile destination) {
            if (!(destination instanceof RootedFile) || !isRemovable()) return false;
            RootedFile to = (RootedFile) destination;
            File source = safe(), target = to.safe();
            return root.equals(to.root) && source != null && target != null && to.isWritable() && !target.exists() &&
                    !target.equals(source) && !target.getPath().startsWith(source.getPath() + File.separator) && source.renameTo(target);
        }
        public List<FtpFile> listFiles() {
            File f = safe(); if (f == null || !f.isDirectory() || !f.canRead()) return null;
            File[] files = f.listFiles(); if (files == null) return null;
            List<FtpFile> result = new ArrayList<>();
            for (File child : files) try {
                String virtual = FtpPaths.child(path, child.getName());
                FtpPaths.confined(root, virtual);
                result.add(new RootedFile(root, virtual, username, writable));
            } catch (IOException ignored) { }
            return result;
        }
        public InputStream createInputStream(long offset) throws IOException {
            if (offset < 0 || !isReadable() || !isFile()) throw new IOException("File is not readable");
            FileInputStream input = new FileInputStream(checked()); input.getChannel().position(offset); return input;
        }
        public OutputStream createOutputStream(long offset) throws IOException {
            if (offset < 0 || !isWritable() || isDirectory()) throw new IOException("File is not writable");
            final RandomAccessFile file = new RandomAccessFile(checked(), "rw");
            file.setLength(offset); file.seek(offset);
            return new OutputStream() {
                public void write(int value) throws IOException { file.write(value); }
                public void write(byte[] bytes, int start, int count) throws IOException { file.write(bytes, start, count); }
                public void close() throws IOException { file.close(); }
            };
        }
    }
}
