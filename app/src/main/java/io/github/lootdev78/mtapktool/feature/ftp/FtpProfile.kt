package io.github.lootdev78.mtapktool.feature.ftp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FtpProfile(
    val name: String,
    val ip: String,
    val port: Int,
    val username: String,
    val password: String,
    val isServerProfile: Boolean,
    val securityType: Int = 0 // 0: FTP, 1: FTPS (Explicit), 2: FTPS (Implicit)
) : Parcelable