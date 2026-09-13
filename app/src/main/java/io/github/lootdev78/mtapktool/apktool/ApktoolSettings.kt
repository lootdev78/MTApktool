package io.github.lootdev78.mtapktool.apktool

import android.content.Context
import android.os.Environment
import java.io.File

data class ApktoolDecodeDefaults(
    val force: Boolean = false,
    val allSources: Boolean = false,
    val noSources: Boolean = false,
    val noDebugInfo: Boolean = false,
    val noResources: Boolean = false,
    val onlyManifest: Boolean = false,
    val matchOriginal: Boolean = false,
    val preserveDirectoryStructure: Boolean = false,
    val keepBrokenResources: Boolean = true,
    val ignoreRawValues: Boolean = false,
    val noAssets: Boolean = false,
    val resourceResolveMode: String = "default",
    val useRegisters: Boolean = true,
    val createNomedia: Boolean = false,
    val removeSplitTraces: Boolean = true,
    val removePropertyTags: Boolean = false,
    val verbose: Boolean = false,
)

data class ApktoolBuildDefaults(
    val force: Boolean = false,
    val debuggable: Boolean = false,
    val copyOriginal: Boolean = false,
    val noCrunch: Boolean = false,
    val networkSecurityConfig: Boolean = false,
    val networkSecurityKeepExisting: Boolean = true,
    val zipalign: Boolean = true,
    val sign: Boolean = true,
    val deleteBuildDirectory: Boolean = false,
    val verbose: Boolean = false,
)

data class ApktoolSignatureDefaults(
    val profile: String = "testkey",
    val customKeystorePath: String = "",
    val customKeystorePassword: String = "",
    val v1: Boolean = true,
    val v2: Boolean = true,
    val v3: Boolean = false,
    val v4: Boolean = false,
)

data class ApktoolGeneralDefaults(
    val notifyOnCompletion: Boolean = true,
    val suppressCompletionWhileOpen: Boolean = true,
    val apkSuffix: String = "",
    val decodeIntoOutputDirectory: Boolean = false,
    val buildIntoOutputDirectory: Boolean = true,
)

object ApktoolSettings {
    private const val PREFS = "mtapktool_settings"

    private const val KEY_FRAMEWORK = "framework_tag"
    private const val KEY_AAPT = "aapt_variant"
    private const val KEY_CUSTOM_AAPT2 = "custom_aapt2_path"
    private const val KEY_WORKERS = "parallel_workers"
    private const val KEY_APKTOOL_THREADS = "apktool_threads"
    private const val KEY_PROJECTS = "projects_root"
    private const val KEY_OUTPUT = "output_root"

    private const val KEY_NOTIFY_DONE = "notify_done"
    private const val KEY_NOTIFY_HIDE_FOREGROUND = "notify_hide_foreground"
    private const val KEY_APK_SUFFIX = "apk_suffix"
    private const val KEY_DECODE_TO_OUTPUT = "decode_to_output"
    private const val KEY_BUILD_TO_OUTPUT = "build_to_output"

    private const val KEY_D_FORCE = "decode_force"
    private const val KEY_D_ALL_SRC = "decode_all_src"
    private const val KEY_D_NO_SRC = "decode_no_src"
    private const val KEY_D_NO_DEBUG = "decode_no_debug"
    private const val KEY_D_NO_RES = "decode_no_res"
    private const val KEY_D_ONLY_MANIFEST = "decode_only_manifest"
    private const val KEY_D_MATCH_ORIGINAL = "decode_match_original"
    private const val KEY_D_PRESERVE_STRUCTURE = "decode_preserve_structure"
    private const val KEY_D_KEEP_BROKEN = "decode_keep_broken"
    private const val KEY_D_IGNORE_RAW = "decode_ignore_raw"
    private const val KEY_D_NO_ASSETS = "decode_no_assets"
    private const val KEY_D_RESOLVE_MODE = "decode_resolve_mode"
    private const val KEY_D_REGISTERS = "decode_use_registers"
    private const val KEY_D_NOMEDIA = "decode_create_nomedia"
    private const val KEY_D_REMOVE_SPLIT = "decode_remove_split_traces"
    private const val KEY_D_REMOVE_PROPERTY = "decode_remove_property"
    private const val KEY_D_VERBOSE = "decode_verbose"

    private const val KEY_B_FORCE = "build_force"
    private const val KEY_B_DEBUGGABLE = "build_debuggable"
    private const val KEY_B_COPY_ORIGINAL = "build_copy_original"
    private const val KEY_B_NO_CRUNCH = "build_no_crunch"
    private const val KEY_B_NET_SEC = "build_net_sec"
    private const val KEY_B_NET_SEC_KEEP_EXISTING = "build_net_sec_keep_existing"
    private const val KEY_B_ALIGN = "build_zipalign"
    private const val KEY_B_SIGN = "build_sign"
    private const val KEY_B_DELETE_BUILD = "build_delete_build_dir"
    private const val KEY_B_VERBOSE = "build_verbose"

    private const val KEY_SIG_PROFILE = "signature_profile"
    private const val KEY_SIG_PATH = "signature_keystore"
    private const val KEY_SIG_PASSWORD = "signature_password"
    private const val KEY_SIG_V1 = "signature_v1"
    private const val KEY_SIG_V2 = "signature_v2"
    private const val KEY_SIG_V3 = "signature_v3"
    private const val KEY_SIG_V4 = "signature_v4"

    const val DEFAULT_FRAMEWORK = "sdk36"
    const val DEFAULT_AAPT = "default"

    val frameworkOptions = listOf("default", "sdk36", "sdk35", "sdk34", "sdk33")
    val aaptOptions = listOf("default", "sdk36", "sdk35", "sdk33", "legacy", "custom")
    val resourceResolveModes = listOf("default", "greedy", "lazy")
    val sourceDecodeModes = listOf("default", "all", "none")
    val resourceDecodeModes = listOf("full", "manifest", "none")
    val signatureProfiles = listOf("testkey", "custom")

    fun frameworkTag(context: Context): String =
        prefs(context).getString(KEY_FRAMEWORK, DEFAULT_FRAMEWORK) ?: DEFAULT_FRAMEWORK

    fun aaptVariant(context: Context): String =
        prefs(context).getString(KEY_AAPT, DEFAULT_AAPT) ?: DEFAULT_AAPT

    fun customAapt2Path(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_AAPT2, "").orEmpty()

    fun maxWorkers(context: Context): Int = prefs(context).getInt(KEY_WORKERS, 2).coerceIn(1, 4)

    fun apktoolThreads(context: Context): Int = prefs(context).getInt(KEY_APKTOOL_THREADS, 2).coerceIn(1, 4)

    fun projectsRoot(context: Context): String =
        prefs(context).getString(KEY_PROJECTS, defaultProjectsRoot()) ?: defaultProjectsRoot()

    fun outputRoot(context: Context): String =
        prefs(context).getString(KEY_OUTPUT, defaultOutputRoot()) ?: defaultOutputRoot()

    fun generalDefaults(context: Context): ApktoolGeneralDefaults {
        val p = prefs(context)
        return ApktoolGeneralDefaults(
            notifyOnCompletion = p.getBoolean(KEY_NOTIFY_DONE, true),
            suppressCompletionWhileOpen = p.getBoolean(KEY_NOTIFY_HIDE_FOREGROUND, true),
            apkSuffix = p.getString(KEY_APK_SUFFIX, "").orEmpty(),
            decodeIntoOutputDirectory = p.getBoolean(KEY_DECODE_TO_OUTPUT, false),
            buildIntoOutputDirectory = p.getBoolean(KEY_BUILD_TO_OUTPUT, true),
        )
    }

    fun decodeDefaults(context: Context): ApktoolDecodeDefaults {
        val p = prefs(context)
        return ApktoolDecodeDefaults(
            force = p.getBoolean(KEY_D_FORCE, false),
            allSources = p.getBoolean(KEY_D_ALL_SRC, false),
            noSources = p.getBoolean(KEY_D_NO_SRC, false),
            noDebugInfo = p.getBoolean(KEY_D_NO_DEBUG, false),
            noResources = p.getBoolean(KEY_D_NO_RES, false),
            onlyManifest = p.getBoolean(KEY_D_ONLY_MANIFEST, false),
            matchOriginal = p.getBoolean(KEY_D_MATCH_ORIGINAL, false),
            preserveDirectoryStructure = p.getBoolean(KEY_D_PRESERVE_STRUCTURE, false),
            keepBrokenResources = p.getBoolean(KEY_D_KEEP_BROKEN, true),
            ignoreRawValues = p.getBoolean(KEY_D_IGNORE_RAW, false),
            noAssets = p.getBoolean(KEY_D_NO_ASSETS, false),
            resourceResolveMode = p.getString(KEY_D_RESOLVE_MODE, "default")
                ?.takeIf { it in resourceResolveModes } ?: "default",
            useRegisters = p.getBoolean(KEY_D_REGISTERS, true),
            createNomedia = p.getBoolean(KEY_D_NOMEDIA, false),
            removeSplitTraces = p.getBoolean(KEY_D_REMOVE_SPLIT, true),
            removePropertyTags = p.getBoolean(KEY_D_REMOVE_PROPERTY, false),
            verbose = p.getBoolean(KEY_D_VERBOSE, false),
        )
    }

    fun buildDefaults(context: Context): ApktoolBuildDefaults {
        val p = prefs(context)
        return ApktoolBuildDefaults(
            force = p.getBoolean(KEY_B_FORCE, false),
            debuggable = p.getBoolean(KEY_B_DEBUGGABLE, false),
            copyOriginal = p.getBoolean(KEY_B_COPY_ORIGINAL, false),
            noCrunch = p.getBoolean(KEY_B_NO_CRUNCH, false),
            networkSecurityConfig = p.getBoolean(KEY_B_NET_SEC, false),
            networkSecurityKeepExisting = p.getBoolean(KEY_B_NET_SEC_KEEP_EXISTING, true),
            zipalign = p.getBoolean(KEY_B_ALIGN, true),
            sign = p.getBoolean(KEY_B_SIGN, true),
            deleteBuildDirectory = p.getBoolean(KEY_B_DELETE_BUILD, false),
            verbose = p.getBoolean(KEY_B_VERBOSE, false),
        )
    }

    fun signatureDefaults(context: Context): ApktoolSignatureDefaults {
        val p = prefs(context)
        return ApktoolSignatureDefaults(
            profile = p.getString(KEY_SIG_PROFILE, "testkey")
                ?.takeIf { it in signatureProfiles } ?: "testkey",
            customKeystorePath = p.getString(KEY_SIG_PATH, "").orEmpty(),
            customKeystorePassword = p.getString(KEY_SIG_PASSWORD, "").orEmpty(),
            v1 = p.getBoolean(KEY_SIG_V1, true),
            v2 = p.getBoolean(KEY_SIG_V2, true),
            v3 = p.getBoolean(KEY_SIG_V3, false),
            v4 = p.getBoolean(KEY_SIG_V4, false),
        )
    }

    fun setFrameworkTag(context: Context, framework: String) {
        val normalized = framework.trim().takeIf { isValidFrameworkTag(it) } ?: DEFAULT_FRAMEWORK
        prefs(context).edit().putString(KEY_FRAMEWORK, normalized).apply()
    }

    fun availableFrameworkTags(context: Context): List<String> {
        val tags = linkedSetOf<String>()
        tags += frameworkOptions
        File(frameworkDir()).listFiles { file -> file.isFile && file.name.endsWith(".apk", ignoreCase = true) }
            ?.forEach { file ->
                Regex("^\\d+-(.+)\\.apk$", RegexOption.IGNORE_CASE)
                    .matchEntire(file.name)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.takeIf { isValidFrameworkTag(it) }
                    ?.let(tags::add)
            }
        frameworkTag(context).takeIf { isValidFrameworkTag(it) }?.let(tags::add)
        return tags.toList()
    }

    fun isBuiltInFramework(tag: String): Boolean = tag in frameworkOptions

    private fun isValidFrameworkTag(tag: String): Boolean =
        tag.isNotBlank() && tag.length <= 80 && tag.matches(Regex("[A-Za-z0-9._-]+"))

    fun setAapt2(context: Context, variant: String, customPath: String = customAapt2Path(context)) {
        prefs(context).edit()
            .putString(KEY_AAPT, variant.takeIf { it in aaptOptions } ?: DEFAULT_AAPT)
            .putString(KEY_CUSTOM_AAPT2, customPath.trim())
            .apply()
    }

    fun saveGeneralDefaults(context: Context, value: ApktoolGeneralDefaults) {
        prefs(context).edit()
            .putBoolean(KEY_NOTIFY_DONE, value.notifyOnCompletion)
            .putBoolean(KEY_NOTIFY_HIDE_FOREGROUND, value.suppressCompletionWhileOpen)
            .putString(KEY_APK_SUFFIX, sanitizeSuffix(value.apkSuffix))
            .putBoolean(KEY_DECODE_TO_OUTPUT, value.decodeIntoOutputDirectory)
            .putBoolean(KEY_BUILD_TO_OUTPUT, value.buildIntoOutputDirectory)
            .apply()
    }

    fun savePathsAndWorkers(
        context: Context,
        workers: Int,
        projectsRoot: String,
        outputRoot: String,
        apktoolThreads: Int = apktoolThreads(context),
    ) {
        prefs(context).edit()
            .putInt(KEY_WORKERS, workers.coerceIn(1, 4))
            .putInt(KEY_APKTOOL_THREADS, apktoolThreads.coerceIn(1, 4))
            .putString(KEY_PROJECTS, projectsRoot.ifBlank { defaultProjectsRoot() })
            .putString(KEY_OUTPUT, outputRoot.ifBlank { defaultOutputRoot() })
            .apply()
        ApktoolJobService.setWorkerLimit(context, workers.coerceIn(1, 4))
    }

    fun setApktoolThreads(context: Context, threads: Int) {
        prefs(context).edit().putInt(KEY_APKTOOL_THREADS, threads.coerceIn(1, 4)).apply()
    }

    fun saveDecodeDefaults(context: Context, value: ApktoolDecodeDefaults) {
        val noResources = value.noResources
        val onlyManifest = value.onlyManifest && !noResources
        val noSources = value.noSources
        val allSources = value.allSources && !noSources
        prefs(context).edit()
            .putBoolean(KEY_D_FORCE, value.force)
            .putBoolean(KEY_D_ALL_SRC, allSources)
            .putBoolean(KEY_D_NO_SRC, noSources)
            .putBoolean(KEY_D_NO_DEBUG, value.noDebugInfo && !noSources)
            .putBoolean(KEY_D_NO_RES, noResources)
            .putBoolean(KEY_D_ONLY_MANIFEST, onlyManifest)
            .putBoolean(KEY_D_MATCH_ORIGINAL, value.matchOriginal)
            .putBoolean(KEY_D_PRESERVE_STRUCTURE, value.preserveDirectoryStructure)
            .putBoolean(KEY_D_KEEP_BROKEN, value.keepBrokenResources && !noResources && !onlyManifest)
            .putBoolean(KEY_D_IGNORE_RAW, value.ignoreRawValues && !noResources)
            .putBoolean(KEY_D_NO_ASSETS, value.noAssets)
            .putString(KEY_D_RESOLVE_MODE, value.resourceResolveMode.takeIf { it in resourceResolveModes } ?: "default")
            .putBoolean(KEY_D_REGISTERS, value.useRegisters && !noSources)
            .putBoolean(KEY_D_NOMEDIA, value.createNomedia)
            .putBoolean(KEY_D_REMOVE_SPLIT, value.removeSplitTraces)
            .putBoolean(KEY_D_REMOVE_PROPERTY, value.removePropertyTags)
            .putBoolean(KEY_D_VERBOSE, value.verbose)
            .apply()
    }

    fun saveBuildDefaults(context: Context, value: ApktoolBuildDefaults) {
        prefs(context).edit()
            .putBoolean(KEY_B_FORCE, value.force)
            .putBoolean(KEY_B_DEBUGGABLE, value.debuggable)
            .putBoolean(KEY_B_COPY_ORIGINAL, value.copyOriginal)
            .putBoolean(KEY_B_NO_CRUNCH, value.noCrunch)
            .putBoolean(KEY_B_NET_SEC, value.networkSecurityConfig)
            .putBoolean(KEY_B_NET_SEC_KEEP_EXISTING, value.networkSecurityKeepExisting)
            .putBoolean(KEY_B_ALIGN, value.zipalign)
            .putBoolean(KEY_B_SIGN, value.sign)
            .putBoolean(KEY_B_DELETE_BUILD, value.deleteBuildDirectory)
            .putBoolean(KEY_B_VERBOSE, value.verbose)
            .apply()
    }

    fun saveSignatureDefaults(context: Context, value: ApktoolSignatureDefaults) {
        prefs(context).edit()
            .putString(KEY_SIG_PROFILE, value.profile.takeIf { it in signatureProfiles } ?: "testkey")
            .putString(KEY_SIG_PATH, value.customKeystorePath.trim())
            .putString(KEY_SIG_PASSWORD, value.customKeystorePassword)
            .putBoolean(KEY_SIG_V1, value.v1)
            .putBoolean(KEY_SIG_V2, value.v2)
            .putBoolean(KEY_SIG_V3, value.v3)
            .putBoolean(KEY_SIG_V4, value.v4)
            .apply()
    }

    /** Compatibility helper for the earlier single-page settings dialog. */
    fun save(context: Context, framework: String, aapt: String, workers: Int, projectsRoot: String) {
        setFrameworkTag(context, framework)
        setAapt2(context, aapt)
        savePathsAndWorkers(context, workers, projectsRoot, outputRoot(context))
    }

    fun resetDefaults(context: Context) {
        prefs(context).edit().clear().apply()
        ApktoolJobService.setWorkerLimit(context, 2)
    }

    fun defaultProjectsRoot(): String =
        Environment.getExternalStorageDirectory().absolutePath + "/apktool/projects"

    fun defaultOutputRoot(): String =
        Environment.getExternalStorageDirectory().absolutePath + "/apktool/output"

    fun frameworkDir(): String =
        Environment.getExternalStorageDirectory().absolutePath + "/apktool/frameworks"

    fun aaptMirrorDir(): String =
        Environment.getExternalStorageDirectory().absolutePath + "/apktool/bin/aapt2/arm64-v8a"

    fun aaptLabel(value: String): String = when (value) {
        "default" -> "Automatisch"
        "sdk36" -> "SDK 36 kompatibel"
        "sdk35" -> "Bundled SDK 35"
        "sdk33" -> "Bundled SDK 33 (16K)"
        "legacy" -> "Originales AAPT2"
        "custom" -> "Benutzerdefiniertes AAPT2"
        else -> value
    }

    fun frameworkLabel(value: String): String = when (value) {
        "default" -> "Eingebaut (SDK 36)"
        "sdk36" -> "SDK 36"
        "sdk35" -> "SDK 35"
        "sdk34" -> "SDK 34"
        "sdk33" -> "SDK 33"
        else -> value
    }

    fun signatureLabel(value: ApktoolSignatureDefaults): String = when (value.profile) {
        "custom" -> FileName.label(value.customKeystorePath).ifBlank { "Benutzerdefinierte Signatur" }
        else -> "Vorgabesignatur (testkey)"
    }

    private fun sanitizeSuffix(value: String): String =
        value.trim().replace(Regex("[\\/\\x00-\\x1f]"), "_").take(48)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private object FileName {
        fun label(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')
    }
}
