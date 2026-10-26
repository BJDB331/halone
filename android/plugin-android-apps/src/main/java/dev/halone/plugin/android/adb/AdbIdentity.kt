package dev.halone.plugin.android.adb

import android.content.Context
import android.util.AtomicFile
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * This installation's ADB identity: a fresh RSA key and self-signed
 * certificate, made once and kept in private storage excluded from backup.
 * Android lists it under Wireless debugging → Paired devices, where it can
 * be revoked. After The Unbound Launcher's (MIT).
 */
internal class AdbIdentity private constructor(val key: PrivateKey, val certificate: X509Certificate) {

    companion object {
        private const val FORMAT = 1

        @Synchronized
        fun load(context: Context): AdbIdentity {
            val file = AtomicFile(File(context.noBackupFilesDir, "adb-identity.bin"))
            if (file.baseFile.exists()) {
                DataInputStream(file.openRead()).use { input ->
                    if (input.readInt() != FORMAT) throw IOException("unknown ADB identity format")
                    val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(block(input)))
                    val certificate = CertificateFactory.getInstance("X.509")
                        .generateCertificate(ByteArrayInputStream(block(input))) as X509Certificate
                    return AdbIdentity(key, certificate)
                }
            }
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            val algorithm = AlgorithmIdentifier(ASN1ObjectIdentifier("1.2.840.113549.1.1.11"), DERNull.INSTANCE)
            val name = X500Name("CN=Halone ADB")
            val body = V3TBSCertificateGenerator().apply {
                setSerialNumber(ASN1Integer(BigInteger(128, SecureRandom()).setBit(127)))
                setSignature(algorithm)
                setIssuer(name)
                setSubject(name)
                setStartDate(Time(Date(System.currentTimeMillis() - DAY_MS)))
                setEndDate(Time(Date(System.currentTimeMillis() + 30L * 365 * DAY_MS)))
                setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(pair.public.encoded))
            }.generateTBSCertificate()
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(pair.private)
                update(body.getEncoded("DER"))
                sign()
            }
            val encoded = DERSequence(arrayOf<ASN1Encodable>(body, algorithm, DERBitString(signature))).getEncoded("DER")
            val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
            certificate.verify(pair.public)
            val stream = file.startWrite()
            try {
                DataOutputStream(stream).apply {
                    writeInt(FORMAT)
                    write(this, pair.private.encoded)
                    write(this, encoded)
                    flush()
                }
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
            return AdbIdentity(pair.private, certificate)
        }

        private const val DAY_MS = 86_400_000L

        private fun block(input: DataInputStream): ByteArray {
            val length = input.readInt()
            if (length < 1 || length > 16384) throw IOException("invalid ADB identity")
            return ByteArray(length).also { input.readFully(it) }
        }

        private fun write(out: DataOutputStream, bytes: ByteArray) {
            out.writeInt(bytes.size)
            out.write(bytes)
        }
    }
}
