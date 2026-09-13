package io.github.apktool.android.runtime;

import java.util.ArrayList;
import java.util.List;

/** Small shell-like tokenizer for the in-app Apktool terminal. No shell is executed. */
public final class ShellTokenizer {
    private ShellTokenizer() {}

    public static List<String> split(String command) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean single = false, dbl = false, escaped = false, started = false;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (escaped) {
                cur.append(ch);
                escaped = false;
                started = true;
                continue;
            }
            if (ch == '\\' && !single) {
                escaped = true;
                started = true;
                continue;
            }
            if (ch == '\'' && !dbl) {
                single = !single;
                started = true;
                continue;
            }
            if (ch == '"' && !single) {
                dbl = !dbl;
                started = true;
                continue;
            }
            if (Character.isWhitespace(ch) && !single && !dbl) {
                if (started) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    started = false;
                }
            } else {
                cur.append(ch);
                started = true;
            }
        }
        if (escaped) cur.append('\\');
        if (single || dbl) throw new IllegalArgumentException("Unclosed quote");
        if (started) out.add(cur.toString());
        return out;
    }

    public static String quote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
