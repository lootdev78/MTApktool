package io.github.lootdev78.mtapktool.feature.ftp

import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.util.*
import java.security.cert.Certificate
import javax.security.auth.x500.X500Principal

object FtpsCertificateUtil {
    private const val ALIAS = "mtapktool-ftps"
    private val DEFAULT_PASSWORD = "mtapktool-ftps".toCharArray()

    fun ensureKeystore(keystoreFile: File): File {
        if (keystoreFile.exists() && keystoreFile.length() > 0) {
            return keystoreFile
        }

        keystoreFile.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }

        val keyStore = KeyStore.getInstance("JKS")
        keyStore.load(null, DEFAULT_PASSWORD)

        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        keystoreFile.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }

        FileOutputStream(keystoreFile).use { fos ->
            keyStore.store(fos, DEFAULT_PASSWORD)
        }

        return keystoreFile
    }

    fun getPassword(): CharArray = DEFAULT_PASSWORD

    fun getPasswordString(): String = String(DEFAULT_PASSWORD)
}