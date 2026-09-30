package android.security.keystore

import java.math.BigInteger
import javax.security.auth.x500.X500Principal
import java.util.Date

object KeyProperties {
    const val KEY_ALGORITHM_RSA = "RSA"
    const val KEY_ALGORITHM_AES = "AES"
    const val KEY_ALGORITHM_EC = "EC"
    const val PURPOSE_ENCRYPT = 1
    const val PURPOSE_DECRYPT = 2
    const val PURPOSE_SIGN = 4
    const val PURPOSE_VERIFY = 8
    const val DIGEST_SHA1 = "SHA-1"
    const val DIGEST_SHA256 = "SHA-256"
    const val DIGEST_SHA512 = "SHA-512"
    const val SIGNATURE_PADDING_RSA_PKCS1 = "PKCS1"
    const val SIGNATURE_PADDING_RSA_PSS = "PSS"
    const val ENCRYPTION_PADDING_NONE = "NoPadding"
    const val ENCRYPTION_PADDING_RSA_PKCS1 = "PKCS1Padding"
    const val BLOCK_MODE_CBC = "CBC"
    const val BLOCK_MODE_GCM = "GCM"
}

class KeyGenParameterSpec private constructor(val keystoreAlias: String, val purposes: Int) : java.security.spec.AlgorithmParameterSpec {
    class Builder(val keystoreAlias: String, val purposes: Int) {
        fun setKeySize(keySize: Int): Builder = this
        fun setDigests(vararg digests: String): Builder = this
        fun setSignaturePaddings(vararg paddings: String): Builder = this
        fun setEncryptionPaddings(vararg paddings: String): Builder = this
        fun setBlockModes(vararg modes: String): Builder = this
        fun setEncryptionRequired(flag: Boolean): Builder = this
        fun setRandomizedEncryptionRequired(flag: Boolean): Builder = this
        fun setCertificateSubject(subject: X500Principal): Builder = this
        fun setCertificateSerialNumber(serial: BigInteger): Builder = this
        fun setCertificateNotBefore(date: Date): Builder = this
        fun setCertificateNotAfter(date: Date): Builder = this
        fun build(): KeyGenParameterSpec = KeyGenParameterSpec(keystoreAlias, purposes)
    }
}
