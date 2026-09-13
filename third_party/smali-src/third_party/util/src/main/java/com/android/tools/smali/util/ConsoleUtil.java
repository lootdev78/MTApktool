/*
 * [The "BSD licence"]
 * Copyright (c) 2010 Ben Gruver
 * Android port hardening: avoid spawning terminal-size shell commands from app sandbox.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 */
package com.android.tools.smali.util;

public class ConsoleUtil {
    private static final int DEFAULT_WIDTH = 80;
    private static final int MIN_WIDTH = 20;
    private static final int MAX_WIDTH = 240;

    /**
     * Android-safe console width. Original smali used host-console probing, which
     * is fragile in a sandboxed app, may block without a TTY, and is not needed
     * for Apktool's embedded UI. The UI/terminal may set smali.console.width;
     * otherwise we use a stable 80-column fallback.
     */
    public static int getConsoleWidth() {
        int width = parsePositiveInt(System.getProperty("smali.console.width"));
        if (width <= 0) width = parsePositiveInt(System.getenv("COLUMNS"));
        if (width <= 0) width = DEFAULT_WIDTH;
        if (width < MIN_WIDTH) return MIN_WIDTH;
        if (width > MAX_WIDTH) return MAX_WIDTH;
        return width;
    }

    private static int parsePositiveInt(String value) {
        if (value == null) return -1;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
