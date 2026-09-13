package io.github.lootdev78.mtapktool.apktool

import android.content.Context
import android.os.Build
import android.os.Environment
import io.github.apktool.android.runtime.ApktoolCommandRunner
import io.github.apktool.android.runtime.Toolchain
import java.io.File

/** Real runtime probe: provisions the supplied Apktool-A module and executes its in-process command runner. */
data class ApktoolRuntimeStatus(
    val ready: Boolean,
    val summary: String,
    val details: String = "",
)

object ApktoolRuntimeVerifier {
    fun verify(context: Context): ApktoolRuntimeStatus {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                error("Zugriff auf alle Dateien fehlt. Bitte MTApktool den Dateizugriff erlauben.")
            }

            val toolchain = Toolchain(context.applicationContext)
            toolchain.provisionDecode()
            val lines = mutableListOf<String>()
            val runner = ApktoolCommandRunner(toolchain) { line ->
                if (lines.size < 20) lines += line
            }
            val versionResult = runner.execute("apktool --version")
            if (!versionResult.isSuccess) error(versionResult.summary)

            val sdk36 = File(toolchain.frameworkDir, "1-sdk36.apk")
            if (!sdk36.isFile || sdk36.length() <= 0L) {
                error("SDK-36-Framework wurde nicht bereitgestellt: ${sdk36.absolutePath}")
            }

            ApktoolRuntimeStatus(
                ready = true,
                summary = "Apktool ${versionResult.summary} bereit",
                details = "Source-Port aktiv • Framework ${sdk36.name} • ${toolchain.runtimePageSize / 1024} KiB Page",
            )
        }.getOrElse { error ->
            ApktoolRuntimeStatus(
                ready = false,
                summary = error.message ?: error.toString(),
                details = error.javaClass.simpleName,
            )
        }
    }
}
