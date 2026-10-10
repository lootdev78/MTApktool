import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;

/** Syntax-only check: unresolved Android symbols are intentionally not type checked. */
public class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<Path> files = new ArrayList<>();
            for (String path : args) files.add(Path.of(path));
            JavacTask task = (JavacTask)compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(files));
            task.parse();
            boolean failed = false;
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) if (diagnostic.getKind() == Diagnostic.Kind.ERROR) { System.err.println(diagnostic); failed = true; }
            if (failed) System.exit(1);
            System.out.println("PASS Java syntax: " + files.size() + " files (syntax only, no Android type check)");
        }
    }
}
