package io.github.lootdev78.mtapktool.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** MT-style explorer preferences shared by settings, panes and file operations. */
data class ExplorerPrefs(
    val accentKey: String = "blue",
    val fileListSize: String = "small",
    val maxFileNameLines: Int = 2,
    val fileListTimePreference: String = "hide_seconds_simplified_year",
    val dateTimeFormat: String = "dd-MM-yyyy HH:mm:ss",
    val disablePermissionInFileList: Boolean = false,
    val generateBackupFile: Boolean = false,
    val preserveFileTime: Boolean = true,
    val customWorkspace: String = Environment.getExternalStorageDirectory().resolve("apktool").absolutePath,
    val recycleBinEnabled: Boolean = false,
    val moveToRecycleBinByDefault: Boolean = false,
    val autoCleanRecycleBinDays: Int = 0,
    val showDeletionWarning: Boolean = true,
    val apkInstallationVerification: Boolean = true,
    val loadExternalThumbnails: Boolean = true,
    val optimizeExternalTransfer: Boolean = true,
    val startupLeft: String = "home",
    val startupRight: String = "home",
    val fileMenuOrder: List<String> = defaultFileMenuOrder,
    val builtInOpenOrder: List<String> = defaultBuiltInOpenOrder,
) {
    companion object {
        val defaultFileMenuOrder = listOf(
            "copy", "move", "tools", "rename", "delete", "compress", "properties", "share", "open_with", "bookmark",
        )
        val defaultBuiltInOpenOrder = listOf(
            "text", "archive", "apk", "split", "apktool", "reveal", "external",
        )
    }
}

object ExplorerPreferences {
    private const val PREFS = "mt_explorer_preferences"
    private var initialized = false
    private lateinit var shared: SharedPreferences
    private val _state = MutableStateFlow(ExplorerPrefs())
    val state: StateFlow<ExplorerPrefs> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        shared = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = read(shared)
        shared.registerOnSharedPreferenceChangeListener { prefs, _ -> _state.value = read(prefs) }
        initialized = true
    }

    fun current(context: Context): ExplorerPrefs {
        init(context)
        return _state.value
    }

    fun update(context: Context, transform: (ExplorerPrefs) -> ExplorerPrefs) {
        init(context)
        val value = transform(_state.value)
        write(shared, value)
        _state.value = value
    }

    fun reset(context: Context) {
        init(context)
        shared.edit().clear().apply()
        _state.value = ExplorerPrefs()
    }

    private fun read(p: SharedPreferences): ExplorerPrefs = ExplorerPrefs(
        accentKey = p.getString("accent_key", "blue") ?: "blue",
        fileListSize = p.getString("file_list_size", "small") ?: "small",
        maxFileNameLines = p.getInt("max_file_name_lines", 2).coerceIn(1, 8),
        fileListTimePreference = p.getString("file_list_time_preference", "hide_seconds_simplified_year") ?: "hide_seconds_simplified_year",
        dateTimeFormat = p.getString("date_time_format", "dd-MM-yyyy HH:mm:ss") ?: "dd-MM-yyyy HH:mm:ss",
        disablePermissionInFileList = p.getBoolean("disable_permission_in_file_list", false),
        generateBackupFile = p.getBoolean("generate_backup_file", false),
        preserveFileTime = p.getBoolean("preserve_file_time", true),
        customWorkspace = p.getString("custom_workspace", Environment.getExternalStorageDirectory().resolve("apktool").absolutePath)
            ?: Environment.getExternalStorageDirectory().resolve("apktool").absolutePath,
        recycleBinEnabled = p.getBoolean("recycle_bin_enabled", false),
        moveToRecycleBinByDefault = p.getBoolean("move_to_recycle_bin_default", false),
        autoCleanRecycleBinDays = p.getInt("auto_clean_recycle_bin_days", 0).coerceAtLeast(0),
        showDeletionWarning = p.getBoolean("show_deletion_warning", true),
        apkInstallationVerification = p.getBoolean("apk_install_verification", true),
        loadExternalThumbnails = p.getBoolean("load_external_thumbnails", true),
        optimizeExternalTransfer = p.getBoolean("optimize_external_transfer", true),
        startupLeft = p.getString("startup_left", "home") ?: "home",
        startupRight = p.getString("startup_right", "home") ?: "home",
        fileMenuOrder = decodeFileMenuOrder(p.getString("file_menu_order", null)),
        builtInOpenOrder = decodeOrder(p.getString("built_in_open_order", null), ExplorerPrefs.defaultBuiltInOpenOrder),
    )

    private fun write(p: SharedPreferences, v: ExplorerPrefs) {
        p.edit()
            .putString("accent_key", v.accentKey)
            .putString("file_list_size", v.fileListSize)
            .putInt("max_file_name_lines", v.maxFileNameLines.coerceIn(1, 8))
            .putString("file_list_time_preference", v.fileListTimePreference)
            .putString("date_time_format", v.dateTimeFormat)
            .putBoolean("disable_permission_in_file_list", v.disablePermissionInFileList)
            .putBoolean("generate_backup_file", v.generateBackupFile)
            .putBoolean("preserve_file_time", v.preserveFileTime)
            .putString("custom_workspace", v.customWorkspace)
            .putBoolean("recycle_bin_enabled", v.recycleBinEnabled)
            .putBoolean("move_to_recycle_bin_default", v.moveToRecycleBinByDefault)
            .putInt("auto_clean_recycle_bin_days", v.autoCleanRecycleBinDays.coerceAtLeast(0))
            .putBoolean("show_deletion_warning", v.showDeletionWarning)
            .putBoolean("apk_install_verification", v.apkInstallationVerification)
            .putBoolean("load_external_thumbnails", v.loadExternalThumbnails)
            .putBoolean("optimize_external_transfer", v.optimizeExternalTransfer)
            .putString("startup_left", v.startupLeft)
            .putString("startup_right", v.startupRight)
            .putString("file_menu_order", v.fileMenuOrder.joinToString(","))
            .putString("built_in_open_order", v.builtInOpenOrder.joinToString(","))
            .apply()
    }

    private fun decodeFileMenuOrder(raw: String?): List<String> {
        // Migrate the order used by older MTApktool builds to the MT-style action order.
        val legacy = "copy,move,delete,rename,tools,compress,properties,share,open_with,bookmark"
        return decodeOrder(if (raw == legacy) null else raw, ExplorerPrefs.defaultFileMenuOrder)
    }

    private fun decodeOrder(raw: String?, defaults: List<String>): List<String> {
        val parsed = raw.orEmpty().split(',').map(String::trim).filter { it.isNotEmpty() && it in defaults }.distinct()
        return parsed + defaults.filterNot(parsed::contains)
    }
}
