package io.github.lootdev78.mtapktool.feature.explorer.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent filename/search rules, shared by the explorer dialogs and their workers. */
public final class FileWorkflow {
    private FileWorkflow() {}

    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory); return FileVisitResult.CONTINUE;
            }
        });
    }

    public static final class SearchSpec {
        public final String query, content;
        public final boolean recursive, matchCase, regex;
        public final long minimum, maximum;
        private final Pattern namePattern;
        public SearchSpec(String query, boolean recursive, boolean matchCase, boolean regex,
                String content, long minimum, long maximum) {
            if (query.isEmpty() && content.isEmpty()) throw new IllegalArgumentException("Suchbegriff oder Textinhalt eingeben");
            if (minimum < -1 || maximum < -1 || (minimum >= 0 && maximum >= 0 && minimum > maximum))
                throw new IllegalArgumentException("Ungültiger Größenbereich");
            this.query = query; this.content = content; this.recursive = recursive;
            this.matchCase = matchCase; this.regex = regex; this.minimum = minimum; this.maximum = maximum;
            namePattern = regex ? Pattern.compile(query, matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) : null;
        }
        public boolean matches(String name, boolean directory, long size) {
            boolean named = namePattern != null ? namePattern.matcher(name).find()
                : normalize(name).contains(normalize(query));
            return named && (directory ? content.isEmpty() && minimum < 0 && maximum < 0
                : (minimum < 0 || size >= minimum) && (maximum < 0 || size <= maximum));
        }
        private String normalize(String text) { return matchCase ? text : text.toLowerCase(Locale.ROOT); }
        /** Bounded streaming content search, including matches split across buffer boundaries. */
        public boolean contains(Reader reader, Runnable checkCancelled) throws IOException {
            if (content.isEmpty()) return true;
            String needle = normalize(content), tail = "";
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                checkCancelled.run();
                String text = normalize(tail + new String(buffer, 0, count));
                if (text.indexOf('\0') >= 0) return false;
                if (text.contains(needle)) return true;
                tail = text.substring(Math.max(0, text.length() - needle.length() + 1));
            }
            return false;
        }
    }

    public static final class RenameSpec {
        public final String template, find, replacement;
        public final boolean regex, matchCase;
        private final Pattern findPattern;
        public RenameSpec(String template, String find, String replacement, boolean regex, boolean matchCase) {
            this.template = template; this.find = find; this.replacement = replacement;
            this.regex = regex; this.matchCase = matchCase;
            findPattern = find.isEmpty() ? null : Pattern.compile(regex ? find : Pattern.quote(find),
                matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        public String name(String original, int index, int total) {
            String processed = findPattern == null ? original : findPattern.matcher(original)
                .replaceAll(regex ? replacement : Matcher.quoteReplacement(replacement));
            if (template.isEmpty()) return processed;
            int dot = processed.lastIndexOf('.');
            String stem = dot > 0 ? processed.substring(0, dot) : processed;
            String suffix = dot > 0 ? processed.substring(dot) : "";
            Matcher tokens = Pattern.compile("\\{(P|S|z?[0-9]+)\\}").matcher(template);
            StringBuffer result = new StringBuffer();
            while (tokens.find()) {
                String token = tokens.group(1), value;
                if (token.equals("P")) value = stem;
                else if (token.equals("S")) value = suffix;
                else {
                    boolean padded = token.startsWith("z");
                    long start = Long.parseLong(padded ? token.substring(1) : token);
                    long number = Math.addExact(start, index);
                    int width = Math.max(2, Long.toString(Math.addExact(start, Math.max(0, total - 1))).length());
                    value = padded ? String.format(Locale.ROOT, "%0" + width + "d", number) : Long.toString(number);
                }
                tokens.appendReplacement(result, Matcher.quoteReplacement(value));
            }
            tokens.appendTail(result);
            return result.toString();
        }
    }

    public static final class RenameEntry {
        public final String id, original, target;
        public RenameEntry(String id, String original, String target) { this.id = id; this.original = original; this.target = target; }
        public boolean unchanged() { return original.equals(target); }
    }

    public static void validName(String name) {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.chars().anyMatch(c -> c < 32 || c == 127)) throw new IllegalArgumentException("Ungültiger Dateiname: " + name);
    }

    /** Keep unchanged names reserved and suffix conflicts; swaps among changing sources are valid. */
    public static List<RenameEntry> resolve(List<RenameEntry> proposed, Set<String> existing) {
        Set<String> reserved = new HashSet<>(existing), originals = new HashSet<>();
        for (RenameEntry entry : proposed) {
            validName(entry.target);
            if (!originals.add(entry.original)) throw new IllegalArgumentException("Doppelte Quelldatei");
            reserved.remove(entry.original);
        }
        for (RenameEntry entry : proposed) if (entry.unchanged()) reserved.add(entry.original);
        List<RenameEntry> resolved = new ArrayList<>();
        for (RenameEntry entry : proposed) {
            if (entry.unchanged()) { resolved.add(entry); continue; }
            String target = entry.target;
            for (int counter = 1; reserved.contains(target); counter++) target = entry.target + " (" + counter + ")";
            reserved.add(target);
            resolved.add(new RenameEntry(entry.id, entry.original, target));
        }
        return resolved;
    }

    public interface RenameStorage {
        boolean exists(String name) throws IOException;
        void move(String source, String target) throws IOException;
        default boolean same(String source, String target) throws IOException { return false; }
    }

    /** Two-stage rename avoids cycles and rolls back completed moves when any step fails. */
    public static void rename(List<RenameEntry> entries, RenameStorage storage, Runnable onStep) throws IOException {
        List<RenameEntry> changing = new ArrayList<>();
        Set<String> originals = new HashSet<>(), targets = new HashSet<>();
        for (RenameEntry entry : entries) {
            validName(entry.original); validName(entry.target);
            if (!originals.add(entry.original) || !targets.add(entry.target)) throw new IOException("Doppelte Umbenennung");
            if (!storage.exists(entry.original)) throw new IOException("Quelle fehlt: " + entry.original);
            if (!entry.unchanged()) changing.add(entry);
        }
        for (RenameEntry entry : changing) if (storage.exists(entry.target) && !originals.contains(entry.target) && !storage.same(entry.original, entry.target))
            throw new IOException("Ziel existiert inzwischen: " + entry.target);
        List<String> temporary = new ArrayList<>();
        for (int i = 0; i < changing.size(); i++) {
            String name;
            do { name = ".mt-rename-" + UUID.randomUUID(); } while (storage.exists(name) || targets.contains(name));
            temporary.add(name);
        }
        int staged = 0, published = 0;
        try {
            while (staged < changing.size()) { storage.move(changing.get(staged).original, temporary.get(staged)); staged++; onStep.run(); }
            while (published < changing.size()) { storage.move(temporary.get(published), changing.get(published).target); published++; onStep.run(); }
        } catch (Exception error) {
            IOException failure = error instanceof IOException ? (IOException) error : new IOException(error);
            for (int i = published - 1; i >= 0; i--) {
                try { storage.move(changing.get(i).target, temporary.get(i)); } catch (Exception rollback) { failure.addSuppressed(rollback); }
            }
            for (int i = staged - 1; i >= 0; i--) {
                try { if (storage.exists(temporary.get(i))) storage.move(temporary.get(i), changing.get(i).original); }
                catch (Exception rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }
}
