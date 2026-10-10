package io.github.lootdev78.mtapktool.feature.explorer.util

import android.system.Os
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

data class UnixFilePermissions(val mode: Int, val uid: Int, val gid: Int, val symlink: Boolean) {
    val octal: String get() = mode.toString(8).padStart(4, '0')
    val symbolic: String get() = (8 downTo 0).joinToString("") { bit -> if (mode and (1 shl bit) == 0) "-" else "rwx"[(8 - bit) % 3].toString() }
}

object FilePermissions {
    fun read(file: File): UnixFilePermissions {
        val stat = Os.lstat(file.absolutePath)
        return UnixFilePermissions(stat.st_mode and 0xFFF, stat.st_uid, stat.st_gid, Files.isSymbolicLink(file.toPath()))
    }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private fun root(command: String) {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw IOException("Root-Anfrage hat das Zeitlimit überschritten")
            val output = process.inputStream.bufferedReader().use { it.readText().take(4096) }
            if (process.exitValue() != 0) throw IOException(output.ifBlank { "Root-Zugriff abgelehnt" })
        } finally { process.destroy() }
    }
    fun apply(file: File, mode: Int, uid: Int, gid: Int, recursive: Boolean, useRoot: Boolean, checkCancelled: () -> Unit) {
        require(mode in 0..0xFFF && uid >= 0 && gid >= 0) { "Ungültige Rechte oder UID/GID" }
        if (Files.isSymbolicLink(file.toPath())) throw IOException("Rechte für symbolische Verknüpfungen werden nicht geändert; Ziel direkt öffnen")
        if (useRoot) {
            checkCancelled()
            val recursion = if (recursive && file.isDirectory) "-R " else ""
            val path = quote(file.absolutePath)
            // chown may clear setuid/setgid bits, so chmod must be applied last.
            root("chown ${recursion}$uid:$gid $path && chmod ${recursion}${mode.toString(8)} $path")
        } else {
            val targets = if (recursive && file.isDirectory) file.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) } else sequenceOf(file)
            for (target in targets) {
                checkCancelled()
                if (Files.isSymbolicLink(target.toPath())) continue
                val old = read(target)
                if (uid != old.uid || gid != old.gid) Os.chown(target.absolutePath, uid, gid)
                Os.chmod(target.absolutePath, mode)
                if (read(target).mode != mode) throw IOException("${target.name}: Das Dateisystem übernimmt diese Unix-Rechte nicht")
            }
        }
        if (read(file).mode != mode) throw IOException("Das Dateisystem hat die angeforderten Rechte nicht übernommen")
    }
}
