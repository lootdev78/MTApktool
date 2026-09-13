/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 */
package brut.util;

import brut.common.BrutException;
import brut.common.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class OS {
    private static final String TAG = OS.class.getName();

    private OS() {
    }

    public static void mkdir(String dir) { mkdir(new File(dir)); }

    public static void mkdir(File dir) {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    public static void rmfile(String file) { rmfile(new File(file)); }

    public static void rmfile(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public static void rmdir(String dir) { rmdir(new File(dir)); }

    public static void rmdir(File dir) {
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) rmdir(file);
            else rmfile(file);
        }
        rmfile(dir);
    }

    public static void mvfile(String src, String dest) throws BrutException { mvfile(new File(src), new File(dest)); }

    public static void mvfile(File src, File dest) throws BrutException {
        try {
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BrutException("Could not move file: " + src, ex);
        }
    }

    public static void cpfile(String src, String dest) throws BrutException { cpfile(new File(src), new File(dest)); }

    public static void cpfile(File src, File dest) throws BrutException {
        ensureNotInterrupted("copy file");
        if (!src.isFile()) return;
        try {
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BrutException("Could not copy file: " + src, ex);
        }
    }

    public static void cpdir(String src, String dest) throws BrutException { cpdir(new File(src), new File(dest)); }

    public static void cpdir(File src, File dest) throws BrutException {
        ensureNotInterrupted("copy directory");
        if (!src.isDirectory()) return;
        mkdir(dest);
        File[] files = src.listFiles();
        if (files == null) return;
        for (File file : files) {
            File destFile = new File(dest, file.getName());
            if (file.isDirectory()) cpdir(file, destFile);
            else cpfile(file, destFile);
        }
    }

    public static void exec(String[] cmd) throws BrutException {
        ensureNotInterrupted("execute process");
        Process ps = null;
        StreamForwarder stdout = null;
        StreamForwarder stderr = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            configureProcess(builder);
            ps = builder.start();

            stderr = new StreamForwarder(ps.getErrorStream(), "ERROR");
            stdout = new StreamForwarder(ps.getInputStream(), "OUTPUT");
            stderr.start();
            stdout.start();

            int exitValue = ps.waitFor();
            stderr.join(TimeUnit.SECONDS.toMillis(3));
            stdout.join(TimeUnit.SECONDS.toMillis(3));
            if (exitValue != 0) {
                throw new BrutException("Execution failed (exit code = " + exitValue + "): " + Arrays.toString(cmd));
            }
        } catch (IOException ex) {
            throw new BrutException("could not exec: " + Arrays.toString(cmd), ex);
        } catch (InterruptedException ex) {
            terminateProcess(ps);
            if (stderr != null) stderr.interrupt();
            if (stdout != null) stdout.interrupt();
            Thread.currentThread().interrupt();
            throw new BrutException("execution interrupted: " + Arrays.toString(cmd), ex);
        }
    }

    public static String execAndReturn(String[] cmd) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.redirectErrorStream(true);
            configureProcess(builder);
            process = builder.start();
            StreamCollector collector = new StreamCollector(process.getInputStream());
            executor.execute(collector);
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return null;
            }
            if (process.exitValue() != 0) {
                Log.w(TAG, "Command returned " + process.exitValue() + ": " + Arrays.toString(cmd));
            }
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            return collector.get();
        } catch (IOException ignored) {
            if (process != null) process.destroy();
            return null;
        } catch (InterruptedException ignored) {
            if (process != null) process.destroy();
            Thread.currentThread().interrupt();
            return null;
        } finally {
            executor.shutdownNow();
        }
    }

    public static File createTempDirectory() throws BrutException {
        try {
            File base = new File(System.getProperty("java.io.tmpdir", System.getProperty("user.dir", ".")));
            if (!base.isDirectory()) mkdir(base);
            File tmp = File.createTempFile("BRUT", null, base);
            if (!tmp.delete()) throw new BrutException("Could not delete tmp file: " + tmp.getAbsolutePath());
            if (!tmp.mkdir()) throw new BrutException("Could not create tmp dir: " + tmp.getAbsolutePath());
            return tmp;
        } catch (IOException ex) {
            throw new BrutException("Could not create tmp dir", ex);
        }
    }

    private static void ensureNotInterrupted(String operation) throws BrutException {
        if (Thread.currentThread().isInterrupted()) {
            throw new BrutException(operation + " interrupted");
        }
    }

    private static void terminateProcess(Process process) {
        if (process == null) return;
        try {
            process.destroy();
            if (process.isAlive() && !process.waitFor(250, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(250, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ignored) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
            try { process.destroyForcibly(); } catch (Throwable ignoredAgain) { }
        }
    }

    private static void configureProcess(ProcessBuilder builder) {
        Map<String, String> env = builder.environment();
        putIfPresent(env, "TMPDIR", System.getProperty("java.io.tmpdir"));
        putIfPresent(env, "HOME", System.getProperty("apktool.android.home"));
        String nativeDir = System.getProperty("apktool.android.nativeLibraryDir");
        if (nativeDir != null && !nativeDir.isEmpty()) {
            String old = env.get("LD_LIBRARY_PATH");
            env.put("LD_LIBRARY_PATH", old == null || old.isEmpty() ? nativeDir : nativeDir + File.pathSeparator + old);
        }
        putIfPresent(env, "ANDROID_DATA", System.getProperty("apktool.android.dataDir"));
        putIfPresent(env, "ANDROID_ROOT", System.getProperty("apktool.android.rootDir"));
    }

    private static void putIfPresent(Map<String, String> env, String key, String value) {
        if (value != null && !value.isEmpty()) env.put(key, value);
    }

    private static class StreamForwarder extends Thread {
        private final InputStream mIn;
        private final String mType;

        StreamForwarder(InputStream in, String type) {
            mIn = in;
            mType = type;
            setName("apktool-" + type.toLowerCase() + "-stream");
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mIn))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (mType.equals("OUTPUT")) Log.i(TAG, line);
                    else Log.w(TAG, line);
                }
            } catch (IOException ex) {
                Log.w(TAG, ex.getMessage());
            }
        }
    }

    private static class StreamCollector implements Runnable {
        private final InputStream mIn;
        private final StringBuilder mBuffer = new StringBuilder();

        StreamCollector(InputStream in) { mIn = in; }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mIn))) {
                String line;
                while ((line = reader.readLine()) != null) mBuffer.append(line).append('\n');
            } catch (IOException ignored) {
            }
        }

        String get() { return mBuffer.toString(); }
    }
}
