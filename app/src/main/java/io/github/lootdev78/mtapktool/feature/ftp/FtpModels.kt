package io.github.lootdev78.mtapktool.feature.ftp

import android.content.Context
import android.net.Uri
import io.github.lootdev78.mtftp.FtpPaths
import io.github.lootdev78.mtftp.MtFtpClient
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FtpProfile(
    val name: String = "",
    val host: String = "",
    val port: Int = 21,
    val username: String = "",
    val directory: String = "/",
    val security: MtFtpClient.Security = MtFtpClient.Security.FTP,
)

object FtpProfileStore {
    fun load(context: Context): List<FtpProfile> = runCatching {
        val array = JSONArray(context.getSharedPreferences("mt_ftp_profiles", 0).getString("profiles", "[]"))
        (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            FtpProfile(value.optString("name"), value.getString("host"), value.optInt("port", 21), value.optString("user"),
                value.optString("directory", "/"), MtFtpClient.Security.valueOf(value.optString("security", "FTP")))
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, profile: FtpProfile) {
        require(profile.name.isNotBlank())
        write(context, (load(context).filterNot { it.name == profile.name } + profile).takeLast(30))
    }
    fun remove(context: Context, name: String) = write(context, load(context).filterNot { it.name == name })
    private fun write(context: Context, profiles: List<FtpProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(JSONObject().put("name", it.name).put("host", it.host).put("port", it.port)
            .put("user", it.username).put("directory", it.directory).put("security", it.security.name)) }
        // Passwords deliberately stay only in the connection form and are never persisted.
        context.getSharedPreferences("mt_ftp_profiles", 0).edit().putString("profiles", array.toString()).apply()
    }
}

object FtpLocation {
    fun isRemote(path: String) = path.startsWith("mtftp://")
    fun session(path: String) = Uri.parse(path).authority ?: error("Missing FTP session")
    fun remotePath(path: String): String = FtpPaths.normalize(Uri.parse(path).path ?: "/")
    fun uri(session: String, path: String): String = Uri.Builder().scheme("mtftp").authority(session).path(FtpPaths.normalize(path)).build().toString()
    fun parent(path: String) = uri(session(path), FtpPaths.parent(remotePath(path)))
    fun child(path: String, name: String) = uri(session(path), FtpPaths.child(remotePath(path), name))
    fun item(session: String, entry: MtFtpClient.Entry) = FileItem(
        file = File(entry.name), remoteUri = uri(session, entry.path), displayName = entry.name,
        directoryOverride = entry.directory, sizeOverride = entry.size, modifiedOverride = entry.modified,
    )
    fun display(profile: FtpProfile, path: String): String {
        val scheme = if (profile.security == MtFtpClient.Security.FTP) "ftp" else "ftps"
        val host = if (profile.host.contains(':')) "[${profile.host}]" else profile.host
        return Uri.Builder().scheme(scheme).encodedAuthority("$host:${profile.port}").path(remotePath(path)).build().toString()
    }
}

data class FileTransferState(val running: Boolean = false, val label: String = "", val completed: Int = 0, val total: Int = 0, val bytes: Long = 0)
