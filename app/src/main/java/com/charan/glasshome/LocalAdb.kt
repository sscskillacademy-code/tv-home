package com.charan.glasshome

import android.content.Context
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Runs a shell command on this same TV through its debugging port (127.0.0.1:5555) — the only way
 * Android 14 lets a home screen really close other apps. Speaks the small ADB protocol directly.
 * The first time, the TV asks "Allow USB debugging?" for this app's key; after "Always allow" it's silent.
 */
object LocalAdb {
    private const val CNXN = 0x4e584e43
    private const val AUTH = 0x48545541
    private const val OPEN = 0x4e45504f
    private const val OKAY = 0x59414b4f
    private const val CLSE = 0x45534c43
    private const val WRTE = 0x45545257
    private const val VERSION = 0x01000000
    private const val MAX_PAYLOAD = 4096
    private const val AUTH_TOKEN = 1
    private const val AUTH_SIGNATURE = 2
    private const val AUTH_RSAPUBLICKEY = 3

    /** Debugging is switched off on the TV (nothing listens on the port). */
    class DebuggingOff : Exception()

    /** The "Allow USB debugging?" prompt was declined or not answered. */
    class NotAllowed : Exception()

    private class Message(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    /** Blocking: call off the main thread. [onPrompt] runs when the TV shows its approval prompt. */
    fun run(ctx: Context, command: String, onPrompt: () -> Unit): String {
        val keys = keyPair(ctx)
        val sock = Socket()
        try {
            try {
                sock.connect(InetSocketAddress("127.0.0.1", 5555), 3000)
            } catch (e: Exception) {
                throw DebuggingOff()
            }
            sock.soTimeout = 10_000
            val input = DataInputStream(BufferedInputStream(sock.getInputStream()))
            val out = BufferedOutputStream(sock.getOutputStream())

            send(out, CNXN, VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray())
            var signed = false
            var keySent = false
            handshake@ while (true) {
                val m = try { read(input) } catch (e: Exception) { throw if (keySent) NotAllowed() else e }
                when (m.cmd) {
                    CNXN -> break@handshake
                    AUTH -> if (m.arg0 == AUTH_TOKEN) {
                        when {
                            !signed -> {
                                send(out, AUTH, AUTH_SIGNATURE, 0, sign(keys, m.data))
                                signed = true
                            }
                            !keySent -> {
                                send(out, AUTH, AUTH_RSAPUBLICKEY, 0, publicKey(keys.public as RSAPublicKey))
                                keySent = true
                                onPrompt()
                                sock.soTimeout = 60_000      // give the person time to press OK
                            }
                            else -> throw NotAllowed()
                        }
                    }
                }
            }

            sock.soTimeout = 60_000
            send(out, OPEN, 1, 0, "shell:$command\u0000".toByteArray())
            val output = StringBuilder()
            session@ while (true) {
                val m = read(input)
                when (m.cmd) {
                    WRTE -> {
                        output.append(String(m.data))
                        send(out, OKAY, 1, m.arg0, ByteArray(0))
                    }
                    CLSE -> {
                        send(out, CLSE, 1, m.arg0, ByteArray(0))
                        break@session
                    }
                }
            }
            return output.toString()
        } finally {
            runCatching { sock.close() }
        }
    }

    // ---------------------------------------------------------------- wire format

    private fun send(out: OutputStream, cmd: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.size)
            .putInt(data.sumOf { it.toInt() and 0xff })
            .putInt(cmd.inv())
        out.write(header.array())
        out.write(data)
        out.flush()
    }

    private fun read(input: DataInputStream): Message {
        val h = ByteArray(24)
        input.readFully(h)
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = b.int
        val arg0 = b.int
        val arg1 = b.int
        val len = b.int
        val data = ByteArray(len)
        if (len > 0) input.readFully(data)
        return Message(cmd, arg0, arg1, data)
    }

    // ---------------------------------------------------------------- keys

    /** This app's own 2048-bit RSA key (made once, kept in private storage). */
    private fun keyPair(ctx: Context): KeyPair {
        val priv = File(ctx.filesDir, "adbkey")
        val pub = File(ctx.filesDir, "adbkey.pub")
        if (priv.exists() && pub.exists()) {
            runCatching {
                val kf = KeyFactory.getInstance("RSA")
                return KeyPair(
                    kf.generatePublic(X509EncodedKeySpec(pub.readBytes())),
                    kf.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes())),
                )
            }
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        priv.writeBytes(kp.private.encoded)
        pub.writeBytes(kp.public.encoded)
        return kp
    }

    /** ADB signs the 20-byte token as if it were a SHA-1 digest (PKCS#1 v1.5). */
    private fun sign(keys: KeyPair, token: ByteArray): ByteArray {
        val digestInfo = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
        val block = ByteArray(256)
        block[0] = 0
        block[1] = 1
        val tail = 1 + digestInfo.size + token.size
        for (i in 2 until 256 - tail) block[i] = 0xff.toByte()
        block[256 - tail] = 0
        digestInfo.copyInto(block, 256 - tail + 1)
        token.copyInto(block, 256 - token.size)
        val c = Cipher.getInstance("RSA/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keys.private)
        return c.doFinal(block)
    }

    /** The public key in Android's own adb format, base64 + name. */
    private fun publicKey(key: RSAPublicKey): ByteArray {
        val words = 64
        val n = key.modulus
        val r32 = BigInteger.ONE.shiftLeft(32)
        val buf = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(words)
        buf.putInt(r32.subtract(n.mod(r32).modInverse(r32)).toInt())
        for (i in 0 until words) buf.putInt(n.shiftRight(32 * i).mod(r32).toInt())
        val rr = BigInteger.ONE.shiftLeft(64 * words).mod(n)
        for (i in 0 until words) buf.putInt(rr.shiftRight(32 * i).mod(r32).toInt())
        buf.putInt(key.publicExponent.toInt())
        val b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP)
        return "$b64 home@tv\u0000".toByteArray()
    }
}
