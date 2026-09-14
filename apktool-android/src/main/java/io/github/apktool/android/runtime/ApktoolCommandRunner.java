package io.github.apktool.android.runtime;

import com.android.apksig.ApkVerifier;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import brut.androlib.ApkBuilder;
import brut.androlib.ApkDecoder;
import brut.androlib.Config;
import brut.androlib.exceptions.AndrolibException;
import brut.androlib.res.AaptManager;
import brut.androlib.res.Framework;
import io.github.abdurazaaqmohammed.apksigner.SignWrapper;
import io.github.muntashirakon.zipalign.ZipAlign;

/**
 * Android-safe command runner that mirrors Apktool CLI commands/options without
 * System.exit(). It is used by both the Android forms UI and terminal UI.
 */
public final class ApktoolCommandRunner {
    public interface Listener {
        void onLine(String line);
    }

    public static final class Result {
        public final int code;
        public final String summary;
        public final File output;

        public Result(int code, String summary, File output) {
            this.code = code;
            this.summary = summary;
            this.output = output;
        }

        public boolean isSuccess() { return code == 0; }
    }

    private static final Object ORIGINAL_CLI_LOCK = new Object();
    private static final InheritableThreadLocal<Listener> JOB_LISTENER = new InheritableThreadLocal<>();

    private final Toolchain toolchain;
    private final Listener listener;
    private boolean quietMode;

    public ApktoolCommandRunner(Toolchain toolchain, Listener listener) {
        this.toolchain = toolchain;
        this.listener = listener == null ? line -> {} : listener;
    }

    public Result execute(String command) throws Exception {
        List<String> args = ShellTokenizer.split(command == null ? "" : command.trim());
        if (args.isEmpty()) return new Result(0, usage(), null);
        if ("apktool-original".equalsIgnoreCase(args.get(0))) {
            args.remove(0);
            return originalCli(args.toArray(new String[0]));
        }
        if ("apktool".equalsIgnoreCase(args.get(0))) args.remove(0);
        if (args.isEmpty()) return new Result(0, usage(), null);

        boolean verboseRequested = args.contains("-v") || args.contains("--verbose");
        boolean quietRequested = args.contains("-q") || args.contains("--quiet");
        quietMode = quietRequested && !verboseRequested;

        JOB_LISTENER.set(listener);
        Handler bridge = installLogBridge();
        try {
            if (verboseRequested && quietRequested) {
                listener.onLine("W: Ignoring -q/--quiet (cannot be used with -v/--verbose)");
            }
            String cmd = args.remove(0);
            String[] rest = args.toArray(new String[0]);
            switch (cmd) {
                case "d": case "decode": return decode(rest);
                case "b": case "build": return build(rest);
                case "if": case "install-framework": return installFramework(rest);
                case "cf": case "clean-frameworks": return cleanFrameworks(rest);
                case "lf": case "list-frameworks": return listFrameworks(rest);
                case "delete-frameworks": return deleteFrameworkFiles(rest);
                case "reset-frameworks": return resetFrameworks(rest);
                case "pr": case "publicize-resources": return publicizeResources(rest);
                case "zipalign": case "align": return zipalign(rest);
                case "apksigner": case "sign": return apksigner(rest);
                case "h": case "help": case "-help": case "--help":
                    return new Result(0, usage(), null);
                case "v": case "version": case "-version": case "--version":
                    return new Result(0, Toolchain.VERSION, null);
                default:
                    throw new IllegalArgumentException("Unrecognized command: " + cmd + "\n\n" + usage());
            }
        } finally {
            Logger.getLogger("").removeHandler(bridge);
            try { bridge.close(); } catch (Exception ignored) {}
            JOB_LISTENER.remove();
        }
    }


    /** Executes the ported upstream brut.apktool.Main without allowing System.exit(). */
    private Result originalCli(String[] args) throws Exception {
        synchronized (ORIGINAL_CLI_LOCK) {
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            PrintStream capturedOut = new PrintStream(new ListenerOutputStream("", listener), true, "UTF-8");
            PrintStream capturedErr = new PrintStream(new ListenerOutputStream("E: ", listener), true, "UTF-8");
            try {
                System.setOut(capturedOut);
                System.setErr(capturedErr);
                int code = brut.apktool.Main.run(args);
                capturedOut.flush();
                capturedErr.flush();
                return new Result(code, code == 0 ? "Original Apktool CLI complete" : "Original Apktool CLI failed (" + code + ")", null);
            } finally {
                capturedOut.flush();
                capturedErr.flush();
                System.setOut(oldOut);
                System.setErr(oldErr);
                capturedOut.close();
                capturedErr.close();
            }
        }
    }

    private static final class ListenerOutputStream extends OutputStream {
        private final String prefix;
        private final Listener listener;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        ListenerOutputStream(String prefix, Listener listener) {
            this.prefix = prefix;
            this.listener = listener;
        }

        @Override public synchronized void write(int b) {
            if (b == '\n') flushLine();
            else if (b != '\r') line.write(b);
        }

        @Override public synchronized void write(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) write(b[i]);
        }

        @Override public synchronized void flush() { flushLine(); }

        private void flushLine() {
            if (line.size() == 0) return;
            listener.onLine(prefix + new String(line.toByteArray(), StandardCharsets.UTF_8));
            line.reset();
        }
    }

    private Result decode(String[] args) throws Exception {
        Options o = new Options();
        addGeneral(o); addDecodeCommon(o);
        o.addOption(Option.builder("f").longOpt("force").build());
        o.addOption(Option.builder("a").longOpt("all-src").build());
        o.addOption(Option.builder("s").longOpt("no-src").build());
        o.addOption(Option.builder().longOpt("no-debug-info").build());
        o.addOption(Option.builder().longOpt("use-registers").build());
        o.addOption(Option.builder("r").longOpt("no-res").build());
        o.addOption(Option.builder().longOpt("only-manifest").build());
        o.addOption(Option.builder().longOpt("res-resolve-mode").hasArg().argName("mode").build());
        o.addOption(Option.builder().longOpt("keep-broken-res").build());
        o.addOption(Option.builder().longOpt("ignore-raw-values").build());
        o.addOption(Option.builder().longOpt("match-original").build());
        o.addOption(Option.builder().longOpt("no-assets").build());
        o.addOption(Option.builder("o").longOpt("output").hasArg().argName("dir").build());

        CommandLine cli = parse(o, args);
        List<String> positional = cli.getArgList();
        if (positional.size() != 1) throw new IllegalArgumentException("decode requires exactly one APK file");
        File apk = new File(positional.get(0));
        if (!apk.isFile()) throw new IOException("Input APK not found: " + apk);

        Config c = baseConfig(cli);
        if (cli.hasOption("force")) c.setForced(true);
        if (cli.hasOption("all-src")) c.setDecodeSources(Config.DecodeSources.FULL);
        if (cli.hasOption("no-src")) {
            if (cli.hasOption("all-src")) optionConflict("-s/--no-src", "-a/--all-src");
            else c.setDecodeSources(Config.DecodeSources.NONE);
        }
        if (cli.hasOption("no-debug-info")) {
            if (cli.hasOption("no-src")) optionConflict("--no-debug-info", "-s/--no-src");
            else c.setBaksmaliDebugMode(false);
        }
        if (cli.hasOption("use-registers")) {
            if (cli.hasOption("no-src")) optionConflict("--use-registers", "-s/--no-src");
            else c.setBaksmaliUseRegisters(true);
        }
        if (cli.hasOption("no-res")) c.setDecodeResources(Config.DecodeResources.NONE);
        if (cli.hasOption("only-manifest")) {
            if (cli.hasOption("no-res")) optionConflict("--only-manifest", "-r/--no-res");
            else c.setDecodeResources(Config.DecodeResources.ONLY_MANIFEST);
        }
        if (cli.hasOption("res-resolve-mode")) {
            if (cli.hasOption("no-res")) {
                optionConflict("--res-resolve-mode", "-r/--no-res");
            } else if (cli.hasOption("only-manifest")) {
                optionConflict("--res-resolve-mode", "--only-manifest");
            } else {
                String mode = cli.getOptionValue("res-resolve-mode");
                if ("greedy".equals(mode)) c.setDecodeResolve(Config.DecodeResolve.GREEDY);
                else if ("lazy".equals(mode)) c.setDecodeResolve(Config.DecodeResolve.LAZY);
                else if ("default".equals(mode)) c.setDecodeResolve(Config.DecodeResolve.DEFAULT);
                else throw new IllegalArgumentException("Unknown resolve resources mode: " + mode + "; expected default, greedy or lazy");
            }
        }
        if (cli.hasOption("keep-broken-res")) {
            if (cli.hasOption("no-res")) optionConflict("--keep-broken-res", "-r/--no-res");
            else if (cli.hasOption("only-manifest")) optionConflict("--keep-broken-res", "--only-manifest");
            else c.setKeepBrokenResources(true);
        }
        if (cli.hasOption("ignore-raw-values")) {
            if (cli.hasOption("no-res")) optionConflict("--ignore-raw-values", "-r/--no-res");
            else c.setIgnoreRawValues(true);
        }
        if (cli.hasOption("match-original")) c.setAnalysisMode(true);
        if (cli.hasOption("no-assets")) c.setDecodeAssets(Config.DecodeAssets.NONE);

        File out;
        if (cli.hasOption("output")) out = new File(cli.getOptionValue("output"));
        else {
            String name = apk.getName().replaceFirst("(?i)\\.apk$", "");
            out = new File(toolchain.getProjectsDir(), name);
        }
        checkCancelled();
        line("Decoding: " + apk.getAbsolutePath());
        line("Framework path: " + c.getFrameworkDirectory() + (c.getFrameworkTag() == null ? "" : " tag=" + c.getFrameworkTag()));
        new ApkDecoder(apk, c).decode(out);
        checkCancelled();
        line("Decoded: " + out.getAbsolutePath());
        return new Result(0, "Decode complete", out);
    }

    private Result build(String[] args) throws Exception {
        Options o = new Options();
        addGeneral(o); addBuildCommon(o);
        o.addOption(Option.builder("f").longOpt("force").build());
        o.addOption(Option.builder().longOpt("no-apk").build());
        o.addOption(Option.builder().longOpt("no-crunch").build());
        o.addOption(Option.builder().longOpt("copy-original").build());
        o.addOption(Option.builder().longOpt("debuggable").build());
        o.addOption(Option.builder().longOpt("net-sec-conf").build());
        o.addOption(Option.builder().longOpt("net-sec-conf-keep-existing").build());
        o.addOption(Option.builder().longOpt("aapt").hasArg().argName("file").build());
        o.addOption(Option.builder().longOpt("aapt-variant").hasArg().argName("default|sdk33|sdk35|sdk36|legacy").build());
        o.addOption(Option.builder("o").longOpt("output").hasArg().argName("file").build());

        CommandLine cli = parse(o, args);
        List<String> positional = cli.getArgList();
        if (positional.size() > 1) throw new IllegalArgumentException("build accepts zero or one project directory");
        File project = new File(positional.isEmpty() ? "." : positional.get(0));
        if (!project.isDirectory()) throw new IOException("Project directory not found: " + project);

        Config c = baseConfig(cli);
        if (cli.hasOption("force")) c.setForced(true);
        if (cli.hasOption("no-apk")) c.setNoApk(true);
        if (cli.hasOption("no-crunch")) c.setNoCrunch(true);
        if (cli.hasOption("copy-original")) c.setCopyOriginal(true);
        if (cli.hasOption("debuggable")) c.setDebuggable(true);
        if (cli.hasOption("net-sec-conf")) c.setNetSecConf(true);
        if (cli.hasOption("net-sec-conf-keep-existing")) {
            if (!cli.hasOption("net-sec-conf")) optionConflict("--net-sec-conf-keep-existing", "missing --net-sec-conf");
            else c.setNetSecConfKeepExisting(true);
        }
        if (cli.hasOption("aapt-variant")) c.setAaptBinary(toolchain.getAaptBinary(cli.getOptionValue("aapt-variant")).getAbsolutePath());
        if (cli.hasOption("aapt")) {
            File aapt = new File(cli.getOptionValue("aapt"));
            if (!aapt.isFile()) throw new IOException("AAPT2 not found: " + aapt);
            AaptManager.getBinaryVersion(aapt);
            c.setAaptBinary(aapt.getAbsolutePath());
        }

        File out = null;
        if (cli.hasOption("output")) {
            if (cli.hasOption("no-apk")) optionConflict("-o/--output", "--no-apk");
            else out = new File(cli.getOptionValue("output"));
        }
        checkCancelled();
        if (out != null && out.getParentFile() != null && !out.getParentFile().isDirectory() && !out.getParentFile().mkdirs()) {
            throw new IOException("Cannot create build output directory: " + out.getParentFile());
        }
        line("Building: " + project.getAbsolutePath());
        line("AAPT2: " + c.getAaptBinary());
        new ApkBuilder(project, c).build(out);
        checkCancelled();
        if (c.isNoApk()) return new Result(0, "Build complete (no APK repack)", null);
        if (out == null) out = new File(new File(project, "dist"), project.getName() + ".apk");
        line("Built: " + out.getAbsolutePath());
        return new Result(0, "Build complete", out);
    }

    private Result installFramework(String[] args) throws Exception {
        Options o = new Options(); addGeneral(o);
        o.addOption(Option.builder("p").longOpt("frame-path").hasArg().build());
        o.addOption(Option.builder("t").longOpt("frame-tag").hasArg().build());
        CommandLine cli = parse(o, args);
        if (cli.getArgList().size() != 1) throw new IllegalArgumentException("install-framework requires one framework APK");
        Config c = toolchain.newConfig();
        applyGeneralAndFramework(c, cli);
        File apk = new File(cli.getArgList().get(0));
        if (!apk.isFile()) throw new IOException("Framework APK not found: " + apk);
        checkCancelled();
        new Framework(c).install(apk);
        checkCancelled();
        return new Result(0, "Framework installed", null);
    }

    private Result cleanFrameworks(String[] args) throws Exception {
        Options o = frameworkMaintenanceOptions();
        CommandLine cli = parse(o, args);
        if (!cli.getArgList().isEmpty()) throw new IllegalArgumentException("clean-frameworks accepts no positional arguments");
        Config c = toolchain.newConfig();
        applyGeneralAndFramework(c, cli);
        if (cli.hasOption("all")) {
            if (cli.hasOption("frame-tag")) optionConflict("-a/--all", "-t/--frame-tag");
            else c.setForced(true);
        }
        checkCancelled();
        new Framework(c).cleanDirectory();
        checkCancelled();
        return new Result(0, "Frameworks cleaned", null);
    }

    private Result listFrameworks(String[] args) throws Exception {
        Options o = frameworkMaintenanceOptions();
        CommandLine cli = parse(o, args);
        if (!cli.getArgList().isEmpty()) throw new IllegalArgumentException("list-frameworks accepts no positional arguments");
        Config c = toolchain.newConfig();
        applyGeneralAndFramework(c, cli);
        if (cli.hasOption("all")) {
            if (cli.hasOption("frame-tag")) optionConflict("-a/--all", "-t/--frame-tag");
            else c.setForced(true);
        }
        checkCancelled();
        List<File> files = new Framework(c).listDirectory();
        checkCancelled();
        if (files.isEmpty()) line("(no framework files)");
        for (File file : files) line(file.getAbsolutePath());
        return new Result(0, files.size() + " framework(s)", null);
    }

    private Result deleteFrameworkFiles(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("delete-frameworks requires at least one framework file name");
        checkCancelled();
        int count = toolchain.deleteFrameworkFiles(Arrays.asList(args));
        checkCancelled();
        line("Deleted frameworks: " + count);
        return new Result(0, "Deleted " + count + " framework(s)", null);
    }

    private Result resetFrameworks(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("reset-frameworks accepts no arguments");
        checkCancelled();
        toolchain.resetFrameworks();
        checkCancelled();
        line("Bundled frameworks SDK33-36 restored");
        return new Result(0, "Frameworks reset", null);
    }

    private Result publicizeResources(String[] args) throws Exception {
        Options o = new Options(); addGeneral(o);
        CommandLine cli = parse(o, args);
        if (cli.getArgList().size() != 1) throw new IllegalArgumentException("publicize-resources requires one resources.arsc file");
        File arsc = new File(cli.getArgList().get(0));
        if (!arsc.isFile()) throw new IOException("resources.arsc not found: " + arsc);
        Config c = toolchain.newConfig();
        checkCancelled();
        new Framework(c).publicizeResources(arsc);
        checkCancelled();
        return new Result(0, "resources.arsc publicized", arsc);
    }

    private Result zipalign(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException(zipalignUsage());
        ArrayList<String> p = new ArrayList<>(Arrays.asList(args));
        boolean check = p.remove("-c") || p.remove("--check");
        boolean force = p.remove("-f") || p.remove("--force");
        int pageAlignment = 0;
        if (p.remove("-p") || p.remove("--page-align-shared-libs")) pageAlignment = 4096;
        int pageIndex = p.indexOf("-P");
        if (pageIndex < 0) pageIndex = p.indexOf("--page-size");
        if (pageIndex >= 0) {
            if (pageIndex + 1 >= p.size()) throw new IllegalArgumentException("-P requires 4, 16 or 64");
            int kb = Integer.parseInt(p.get(pageIndex + 1));
            if (kb != 4 && kb != 16 && kb != 64) throw new IllegalArgumentException("-P must be 4, 16 or 64");
            pageAlignment = kb * 1024;
            p.remove(pageIndex + 1);
            p.remove(pageIndex);
        }
        if ((check && p.size() != 2) || (!check && p.size() != 3)) {
            throw new IllegalArgumentException(zipalignUsage());
        }
        int alignment = Integer.parseInt(p.get(0));
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("Alignment must be a positive power of two");
        }
        File input = new File(p.get(1));
        if (!input.isFile()) throw new IOException("Input archive not found: " + input);
        if (check) {
            boolean ok = ZipAlign.isZipAligned(input.getAbsolutePath(), alignment, pageAlignment);
            line(ok ? "Verification successful" : "Verification failed");
            return new Result(ok ? 0 : 1, ok ? "Aligned" : "Not aligned", input);
        }
        File output = new File(p.get(2));
        ensureDifferentFiles(input, output, "zipalign input and output must be different files");
        ensureParentDirectory(output);
        checkCancelled();
        boolean ok = ZipAlign.doZipAlign(input.getAbsolutePath(), output.getAbsolutePath(), alignment, pageAlignment, force);
        checkCancelled();
        if (!ok) throw new IOException("zipalign returned failure");
        line("Aligned: " + output.getAbsolutePath());
        return new Result(0, "Zipalign complete", output);
    }

    private static String zipalignUsage() {
        return "zipalign [-c] [-f] [-p | -P 4|16|64] <alignment> <input> [output]";
    }

    private Result apksigner(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("apksigner sign|verify ...");
        String sub = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        if ("verify".equals(sub)) {
            if (rest.length != 1) throw new IllegalArgumentException("apksigner verify <apk>");
            File apk = new File(rest[0]);
            if (!apk.isFile()) throw new IOException("APK not found: " + apk);
            checkCancelled();
            ApkVerifier.Result r = new ApkVerifier.Builder(apk).build().verify();
            checkCancelled();
            line("verified=" + r.isVerified());
            line("v1=" + r.isVerifiedUsingV1Scheme() + " v2=" + r.isVerifiedUsingV2Scheme()
                    + " v3=" + r.isVerifiedUsingV3Scheme() + " v3.1=" + r.isVerifiedUsingV31Scheme()
                    + " v4=" + r.isVerifiedUsingV4Scheme());
            return new Result(r.isVerified() ? 0 : 1, r.isVerified() ? "Signature verified" : "Signature verification failed", apk);
        }
        if (!"sign".equals(sub)) throw new IllegalArgumentException("apksigner sign|verify ...");
        Options o = new Options();
        o.addOption(Option.builder("o").longOpt("out").hasArg().build());
        o.addOption(Option.builder().longOpt("ks").hasArg().build());
        o.addOption(Option.builder().longOpt("ks-pass").hasArg().build());
        o.addOption(Option.builder().longOpt("v1-signing-enabled").hasArg().build());
        o.addOption(Option.builder().longOpt("v2-signing-enabled").hasArg().build());
        o.addOption(Option.builder().longOpt("v3-signing-enabled").hasArg().build());
        o.addOption(Option.builder().longOpt("v4-signing-enabled").hasArg().build());
        CommandLine cli = parse(o, rest);
        if (cli.getArgList().size() != 1) throw new IllegalArgumentException("apksigner sign [options] <apk>");
        File input = new File(cli.getArgList().get(0));
        if (!input.isFile()) throw new IOException("APK not found: " + input);
        File output = cli.hasOption("out") ? new File(cli.getOptionValue("out"))
                : new File(input.getParentFile(), input.getName().replaceFirst("(?i)\\.apk$", "") + "-signed.apk");
        ensureDifferentFiles(input, output, "apksigner input and output must be different files");
        ensureParentDirectory(output);
        File ks = cli.hasOption("ks") ? new File(cli.getOptionValue("ks")) : toolchain.getDebugKeystore();
        if (!ks.isFile()) throw new IOException("Signing keystore not found: " + ks);
        String pass = cli.getOptionValue("ks-pass", "android");
        boolean v1 = bool(cli, "v1-signing-enabled", true);
        boolean v2 = bool(cli, "v2-signing-enabled", true);
        boolean v3 = bool(cli, "v3-signing-enabled", true);
        boolean v4 = bool(cli, "v4-signing-enabled", false);
        if (!v1 && !v2 && !v3 && !v4) {
            throw new IllegalArgumentException("At least one APK signing scheme must be enabled");
        }
        deleteExistingOutput(output);
        if (v4) deleteExistingOutput(new File(output.getAbsolutePath() + ".idsig"));
        checkCancelled();
        new SignWrapper(ks.getAbsolutePath(), pass, v1, v2, v3, v4).signApk(input, output);
        checkCancelled();
        line("Signed: " + output.getAbsolutePath());
        return new Result(0, "Signing complete", output);
    }

    public Result postProcessBuild(Result built, boolean align, boolean sign) throws Exception {
        return postProcessBuild(built, align, sign, null, "android", true, true, true, false);
    }

    public Result postProcessBuild(Result built, boolean align, boolean sign, String keystorePath,
            String keystorePassword, boolean v1, boolean v2, boolean v3, boolean v4) throws Exception {
        if (built == null || !built.isSuccess() || built.output == null || (!align && !sign)) return built;
        File current = built.output;
        if (align) {
            checkCancelled();
            File aligned = sibling(current, "-aligned.apk");
            deleteExistingOutput(aligned);
            line("Zipalign: " + current.getName());
            if (!ZipAlign.doZipAlign(current.getAbsolutePath(), aligned.getAbsolutePath(), 4, 16 * 1024, true)) {
                throw new IOException("zipalign returned failure");
            }
            checkCancelled();
            current = aligned;
        }
        if (sign) {
            if (!v1 && !v2 && !v3 && !v4) {
                throw new IllegalArgumentException("At least one APK signing scheme must be enabled");
            }
            checkCancelled();
            File signed = sibling(current, "-signed.apk");
            deleteExistingOutput(signed);
            File signedV4 = new File(signed.getAbsolutePath() + ".idsig");
            if (v4) deleteExistingOutput(signedV4);
            File ks = keystorePath == null || keystorePath.trim().isEmpty() ? toolchain.getDebugKeystore() : new File(keystorePath);
            if (!ks.isFile()) throw new IOException("Signing keystore not found: " + ks);
            String pass = keystorePassword == null || keystorePassword.isEmpty() ? "android" : keystorePassword;
            line("Signing v1=" + v1 + " v2=" + v2 + " v3=" + v3 + " v4=" + v4 + ": " + current.getName());
            new SignWrapper(ks.getAbsolutePath(), pass, v1, v2, v3, v4).signApk(current, signed);
            checkCancelled();
            current = signed;
        }
        return new Result(0, "Build pipeline complete", current);
    }

    private static File sibling(File file, String suffix) {
        String base = file.getName().replaceFirst("(?i)\\.apk$", "");
        return new File(file.getParentFile(), base + suffix);
    }

    private Config baseConfig(CommandLine cli) throws IOException {
        Config c = toolchain.newConfig();
        applyGeneralAndFramework(c, cli);
        if (cli.hasOption("jobs")) {
            final String value = cli.getOptionValue("jobs");
            final int jobs;
            try {
                jobs = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("--jobs must be an integer between 1 and 4: " + value, ex);
            }
            if (jobs < 1 || jobs > 4) {
                throw new IllegalArgumentException("--jobs must be between 1 and 4 on Android: " + jobs);
            }
            c.setJobs(jobs);
        }
        if (cli.hasOption("lib")) {
            Map<String, String[]> libs = c.getLibraryFiles();
            for (String entry : cli.getOptionValues("lib")) {
                String[] split = entry.split(":", 2);
                if (split.length != 2) throw new IllegalArgumentException("Invalid --lib, expected package:file[,file]: " + entry);
                libs.put(split[0], split[1].split(","));
            }
        }
        return c;
    }

    private void applyGeneralAndFramework(Config c, CommandLine cli) {
        if (cli.hasOption("verbose")) {
            c.setVerbose(true);
            Logger.getLogger("").setLevel(Level.FINE);
        } else if (cli.hasOption("quiet")) {
            Logger.getLogger("").setLevel(Level.SEVERE);
        } else {
            Logger.getLogger("").setLevel(Level.INFO);
        }
        if (cli.hasOption("frame-path")) c.setFrameworkDirectory(cli.getOptionValue("frame-path"));
        if (cli.hasOption("frame-tag")) c.setFrameworkTag(cli.getOptionValue("frame-tag"));
    }

    private static void addGeneral(Options o) {
        o.addOption(Option.builder("v").longOpt("verbose").build());
        o.addOption(Option.builder("q").longOpt("quiet").build());
    }

    private static void addDecodeCommon(Options o) {
        o.addOption(Option.builder("j").longOpt("jobs").hasArg().argName("num").build());
        o.addOption(Option.builder("p").longOpt("frame-path").hasArg().argName("dir").build());
        o.addOption(Option.builder("t").longOpt("frame-tag").hasArg().argName("tag").build());
        o.addOption(Option.builder("l").longOpt("lib").hasArg().argName("package:file").build());
    }

    private static void addBuildCommon(Options o) {
        o.addOption(Option.builder("j").longOpt("jobs").hasArg().argName("num").build());
        o.addOption(Option.builder("p").longOpt("frame-path").hasArg().argName("dir").build());
        o.addOption(Option.builder("t").longOpt("frame-tag").hasArg().argName("tag").build());
        o.addOption(Option.builder("l").longOpt("lib").hasArg().argName("package:file").build());
    }

    private static Options frameworkMaintenanceOptions() {
        Options o = new Options(); addGeneral(o);
        o.addOption(Option.builder("a").longOpt("all").build());
        o.addOption(Option.builder("p").longOpt("frame-path").hasArg().build());
        o.addOption(Option.builder("t").longOpt("frame-tag").hasArg().build());
        return o;
    }

    private static CommandLine parse(Options o, String[] args) throws ParseException {
        return new DefaultParser(false).parse(o, args, false);
    }

    private static boolean bool(CommandLine cli, String name, boolean def) {
        if (!cli.hasOption(name)) return def;
        String value = cli.getOptionValue(name);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("--" + name + " must be true or false: " + value);
    }

    private static void deleteExistingOutput(File output) throws IOException {
        if (output.exists() && !output.delete()) {
            throw new IOException("Cannot replace existing output: " + output);
        }
    }

    private static void ensureParentDirectory(File output) throws IOException {
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }
    }

    private static void ensureDifferentFiles(File input, File output, String message) throws IOException {
        if (input.getCanonicalFile().equals(output.getCanonicalFile())) {
            throw new IllegalArgumentException(message);
        }
    }

    private void optionConflict(String option, String conflict) {
        line("W: Ignoring " + option + " (cannot be used with " + conflict + ")");
    }

    private Handler installLogBridge() {
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) {
                if (JOB_LISTENER.get() != listener || !isLoggable(record) || quietMode) return;
                try { listener.onLine(getFormatter().format(record)); }
                catch (RuntimeException ex) { reportError(null, ex, ErrorManager.WRITE_FAILURE); }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter() {
            @Override public String format(LogRecord record) {
                int level = record.getLevel().intValue();
                String prefix = level >= Level.SEVERE.intValue() ? "E"
                        : level >= Level.WARNING.intValue() ? "W"
                        : level >= Level.INFO.intValue() ? "I" : "D";
                return prefix + ": " + record.getMessage();
            }
        });
        Logger.getLogger("").addHandler(handler);
        return handler;
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Job cancelled");
    }

    private void line(String line) {
        if (!quietMode) listener.onLine(line);
    }

    public static String usage() {
        return "Apktool Android " + Toolchain.VERSION + "\n"
                + "\nOriginal Apktool commands:\n"
                + "  apktool d|decode [options] <apk-file>\n"
                + "    -f --force, -a --all-src, -s --no-src, --no-debug-info, -r --no-res,\n"
                + "    --only-manifest, --res-resolve-mode default|greedy|lazy, --keep-broken-res,\n"
                + "    --ignore-raw-values, --match-original, --no-assets, --use-registers, -o --output,\n"
                + "    -j --jobs, -p --frame-path, -t --frame-tag, -l --lib, -v --verbose, -q --quiet\n"
                + "  apktool b|build [options] <apk-dir>\n"
                + "    -f --force, --no-apk, --no-crunch, --copy-original, --debuggable,\n"
                + "    --net-sec-conf [--net-sec-conf-keep-existing], --aapt <file>, -o --output, -j, -p, -t, -l, -v, -q\n"
                + "  apktool if|install-framework [-p dir] [-t tag] <framework.apk>\n"
                + "  apktool cf|clean-frameworks [-a] [-p dir] [-t tag]\n"
                + "  apktool lf|list-frameworks [-a] [-p dir] [-t tag]\n"
                + "  apktool pr|publicize-resources <resources.arsc>\n"
                + "\nAndroid integration helpers:\n"
                + "  zipalign [-c] [-f] [-p | -P 4|16|64] <alignment> <input> [output]\n"
                + "  apksigner sign [--ks file] [--ks-pass pass] [-o out.apk] <apk>\n"
                + "  apksigner verify <apk>\n"
                + "  build-only helper: --aapt-variant default|sdk33|sdk35|sdk36|legacy\n"
                + "  apktool-original <original apktool arguments>  (direct ported upstream CLI engine)\n";
    }
}
