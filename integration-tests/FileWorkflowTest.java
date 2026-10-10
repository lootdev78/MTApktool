import io.github.lootdev78.mtapktool.feature.explorer.util.FileWorkflow;
import io.github.lootdev78.mtapktool.feature.explorer.util.FileWorkflow.RenameEntry;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class FileWorkflowTest {
    private static int assertions;
    private static void check(boolean test, String label) { if (!test) throw new AssertionError(label); assertions++; }
    private static void rejected(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(label);
    }
    private static final class Storage implements FileWorkflow.RenameStorage {
        final Path root; int moves; int failAt = -1;
        Storage(Path root) { this.root = root; }
        public boolean exists(String name) { return Files.exists(root.resolve(name)); }
        public void move(String source, String target) throws IOException {
            if (++moves == failAt) throw new IOException("Injected failure");
            if (exists(target)) throw new IOException("Existing target");
            Files.move(root.resolve(source), root.resolve(target));
        }
    }
    public static void main(String[] args) throws Exception {
        var search = new FileWorkflow.SearchSpec("README", true, false, false, "", 1, 30);
        check(search.matches("readme.md", false, 10), "insensitive name/size");
        check(!search.matches("readme.md", false, 31), "size maximum");
        check(!search.matches("readme.md", false, 0), "size minimum");
        check(!search.matches("README", true, 0), "size-filtered folders excluded");
        check(!new FileWorkflow.SearchSpec("README", false, true, false, "", -1, -1).matches("readme", false, 0), "case-sensitive name");
        check(new FileWorkflow.SearchSpec("^a.*\\.txt$", false, false, true, "", -1, -1).matches("ABC.TXT", false, 20), "name regex");
        rejected(() -> new FileWorkflow.SearchSpec("[", false, false, true, "", -1, -1), "invalid regex");
        rejected(() -> new FileWorkflow.SearchSpec("a", false, false, false, "", 10, 1), "invalid size range");
        var content = new FileWorkflow.SearchSpec("", false, false, false, "needle", -1, -1);
        check(content.contains(new StringReader("x".repeat(8190) + "NEEDLE"), () -> {}), "content across chunks");
        check(!content.contains(new StringReader("not matching"), () -> {}), "content miss");
        check(!content.contains(new StringReader("binary\0needle"), () -> {}), "binary guard");
        check(content.matches("anything", false, 12), "content-only search");
        var template = new FileWorkflow.RenameSpec("{P}-{z9}{S}", "a", "$", false, false);
        check(template.name("AB.txt", 1, 3).equals("$B-10.txt"), "literal replacement and numbered template");
        check(new FileWorkflow.RenameSpec("{P}{S}", "", "", false, false).name(".profile", 0, 1).equals(".profile"), "dotfile preservation");
        check(new FileWorkflow.RenameSpec("{1}-{P}{S}", "", "", false, false).name("a.tar.gz", 0, 1).equals("1-a.tar.gz"), "last extension");
        check(new FileWorkflow.RenameSpec("", "^(.*)\\.txt$", "$1.md", true, true).name("abc.txt", 0, 1).equals("abc.md"), "regex capture replacement");
        rejected(() -> FileWorkflow.validName("../escape"), "unsafe name");
        rejected(() -> FileWorkflow.validName("bad\nname"), "control character");
        var proposed = List.of(new RenameEntry("A", "A", "B"), new RenameEntry("B", "B", "B"));
        var resolved = FileWorkflow.resolve(proposed, Set.of("A", "B", "B (1)"));
        check(resolved.get(0).target.equals("B (2)") && resolved.get(1).target.equals("B"), "unchanged and existing targets reserved");
        Path root = Files.createTempDirectory("mt-rename-test-");
        try {
            Files.writeString(root.resolve("A"), "first"); Files.writeString(root.resolve("B"), "second");
            var cycle = List.of(new RenameEntry("A", "A", "B"), new RenameEntry("B", "B", "A"));
            FileWorkflow.rename(cycle, new Storage(root), () -> {});
            check(Files.readString(root.resolve("A")).equals("second") && Files.readString(root.resolve("B")).equals("first"), "real filesystem swap");
            for (int failure : new int[] {2, 4}) {
                Storage failing = new Storage(root); failing.failAt = failure;
                try { FileWorkflow.rename(cycle, failing, () -> {}); throw new AssertionError("failure missing"); }
                catch (IOException expected) { check(expected.getSuppressed().length == 0, "rollback completed"); }
                check(Files.readString(root.resolve("A")).equals("second") && Files.readString(root.resolve("B")).equals("first"), "contents preserved after failure " + failure);
                try (var files = Files.list(root)) { check(files.count() == 2, "no staged files after rollback"); }
            }
            try { FileWorkflow.rename(cycle, new Storage(root), () -> { throw new IllegalStateException("Progress failed"); }); throw new AssertionError(); }
            catch (IOException expected) { check(Files.readString(root.resolve("A")).equals("second"), "rollback after callback failure"); }
            Files.writeString(root.resolve("C"), "external");
            try { FileWorkflow.rename(List.of(new RenameEntry("A", "A", "C")), new Storage(root), () -> {}); throw new AssertionError(); }
            catch (IOException expected) { check(Files.readString(root.resolve("C")).equals("external"), "external target never overwritten"); }
        } finally { try (var files = Files.list(root)) { for (Path p : files.toList()) Files.delete(p); } Files.delete(root); }
        Path outer = Files.createTempDirectory("mt-delete-outside-");
        Path deleting = Files.createTempDirectory("mt-delete-tree-");
        try {
            Files.writeString(outer.resolve("keep"), "outside");
            Files.createSymbolicLink(deleting.resolve("link"), outer);
            Files.writeString(deleting.resolve("inside"), "delete");
            FileWorkflow.deleteTree(deleting);
            check(Files.readString(outer.resolve("keep")).equals("outside"), "delete never follows directory symlinks");
            check(!Files.exists(deleting), "requested tree removed");
        } finally { FileWorkflow.deleteTree(deleting); FileWorkflow.deleteTree(outer); }
        System.out.println("PASS file workflows: " + assertions + " assertions");
    }
}
