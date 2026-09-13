package io.github.lootdev78.mtapktool.apktool

import io.github.apktool.android.runtime.ApktoolCommandRunner
import java.io.File
import java.util.concurrent.CancellationException

object ProjectPostProcessor {
    data class Options(
        val createNomedia: Boolean = false,
        val removeSplitTraces: Boolean = false,
        val removePropertyTags: Boolean = false,
    )

    fun process(root: File, options: Options, listener: ApktoolCommandRunner.Listener) {
        if (!root.exists()) return
        val projects = if (File(root, "apktool.yml").isFile) listOf(root)
        else root.listFiles()?.filter { it.isDirectory && File(it, "apktool.yml").isFile }.orEmpty()
        projects.forEach { project ->
            checkCancelled()
            if (options.createNomedia) {
                val marker = File(project, ".nomedia")
                if (!marker.exists()) marker.createNewFile()
                listener.onLine("I: .nomedia erstellt: ${project.name}")
            }
            if (options.removeSplitTraces || options.removePropertyTags) {
                cleanManifest(File(project, "AndroidManifest.xml"), options, listener)
            }
        }
    }

    private fun cleanManifest(manifest: File, options: Options, listener: ApktoolCommandRunner.Listener) {
        if (!manifest.isFile) return
        checkCancelled()
        val original = manifest.readText()
        var text = original
        if (options.removeSplitTraces) {
            text = text
                .replace(Regex("(?is)\\s*<uses-split\\b[^>]*/>"), "")
                .replace(Regex("(?i)\\s+(?:android:)?(?:isSplitRequired|requiredSplitTypes|splitTypes|splitName|configForSplit|isFeatureSplit|split)=\\\"[^\\\"]*\\\""), "")
        }
        if (options.removePropertyTags) {
            text = text
                .replace(Regex("(?is)\\s*<property\\b[^>]*/>"), "")
                .replace(Regex("(?is)\\s*<property\\b[^>]*>.*?</property\\s*>"), "")
        }
        if (text != original) {
            val backup = File(manifest.parentFile, "AndroidManifest.xml.mtapktool.bak")
            if (!backup.exists()) backup.writeText(original)
            manifest.writeText(text)
            listener.onLine("I: Manifest bereinigt: ${manifest.parentFile?.name ?: manifest.name}")
        }
    }

    private fun checkCancelled() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Job cancelled")
    }
}
