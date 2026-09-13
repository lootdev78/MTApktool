package com.starry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class FileUtils {
    private FileUtils() {}

    public static InputStream getInputStream(File file) throws IOException {
        return new FileInputStream(file);
    }

    public static OutputStream getOutputStream(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent);
        }
        return new FileOutputStream(file);
    }
}
