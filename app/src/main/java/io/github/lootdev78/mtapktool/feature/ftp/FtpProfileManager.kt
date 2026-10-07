package io.github.lootdev78.mtapktool.feature.ftp

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FtpProfileManager(private val context: Context) {
    private val gson = Gson()
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val profilesKey = "ftp_profiles"
    private var profiles: MutableList<FtpProfile> = mutableListOf()

    init {
        loadProfiles()
        ensureDefaultProfiles()
    }

    private fun loadProfiles() {
        val json = prefs.getString(profilesKey, null) ?: return
        val type = object : TypeToken<List<FtpProfile>>() {}.type
        profiles = gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveProfiles() {
        val json = gson.toJson(profiles)
        prefs.edit().putString(profilesKey, json).apply()
    }

    private fun ensureDefaultProfiles() {
        if (profiles.isEmpty()) {
            profiles.add(
                FtpProfile(
                    name = "Default Server",
                    ip = "192.168.1.1",
                    port = 2121,
                    username = "admin",
                    password = "admin",
                    isServerProfile = true
                )
            )
            profiles.add(
                FtpProfile(
                    name = "Default Client",
                    ip = "192.168.1.1",
                    port = 2121,
                    username = "admin",
                    password = "admin",
                    isServerProfile = false
                )
            )
            saveProfiles()
        }
    }

    fun getProfiles(): List<FtpProfile> = profiles.toList()

    fun getServerProfiles(): List<FtpProfile> = profiles.filter { it.isServerProfile }

    fun getClientProfiles(): List<FtpProfile> = profiles.filter { !it.isServerProfile }

    fun addProfile(profile: FtpProfile) {
        profiles.add(profile)
        saveProfiles()
    }

    fun updateProfile(index: Int, profile: FtpProfile) {
        if (index in profiles.indices) {
            profiles[index] = profile
            saveProfiles()
        }
    }

    fun deleteProfile(index: Int) {
        if (profiles.size > 1 && index in profiles.indices) {
            profiles.removeAt(index)
            saveProfiles()
        }
    }
}