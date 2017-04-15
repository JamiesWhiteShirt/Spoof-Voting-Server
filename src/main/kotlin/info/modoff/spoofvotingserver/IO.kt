package info.modoff.spoofvotingserver

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

fun DataInputStream.readVarInt(): Int {
    var result = 0
    for (i in 0..5) {
        if (i >= 5) {
            throw IOException("VarInt too big")
        }
        val byte = readByte().toInt()
        result = result or ((byte and 127) shl (i * 7))
        if ((byte and 128) != 128) {
            break
        }
    }
    return result
}

fun DataInputStream.readString(maxLength: Int): String {
    val length = readVarInt()
    if (length > maxLength) {
        throw IOException("The received encoded string buffer length is longer than maximum allowed ($length > $maxLength)")
    } else if (length < 0) {
        throw IOException("The received encoded string buffer length is less than zero! Weird string!")
    }

    val bytes = ByteArray(length)
    read(bytes)
    val result = String(bytes, Charsets.UTF_8)
    if (result.length > maxLength) {
        throw IOException("The received string length is longer than maximum allowed ($length > $maxLength)")
    }

    return result
}

fun DataInputStream.readByteArray(): ByteArray {
    val length = readVarInt()
    val bytes = ByteArray(length)
    read(bytes)
    return bytes
}

fun DataOutputStream.writeVarInt(value: Int) {
    var value = value
    while ((value and -128) != 0) {
        writeByte((value and 127) or 128)
        value = value ushr 7
    }

    writeByte(value)
}

fun DataOutputStream.writeByteArray(value: ByteArray) {
    writeVarInt(value.size)
    write(value)
}

fun DataOutputStream.writeString(value: String) {
    writeByteArray(value.toByteArray(Charsets.UTF_8))
}
