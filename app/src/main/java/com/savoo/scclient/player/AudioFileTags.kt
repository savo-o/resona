package com.savoo.scclient.player

import com.savoo.scclient.data.model.OfflineTrack
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

internal object AudioFileTags {
    fun write(source: File, destination: File, track: OfflineTrack, cover: ByteArray?) {
        if (destination.extension == "mp3") writeMp3(source, destination, track, cover)
        else writeMp4(source, destination, track, cover)
    }

    private fun metadata(track: OfflineTrack) = JSONObject().apply {
        put("trackId", track.trackId)
        put("title", track.title)
        put("artist", track.username)
        put("genre", track.genre)
        put("durationMs", track.durationMs)
        put("permalinkUrl", track.permalinkUrl)
        put("userId", track.userId)
        put("userAvatarUrl", track.userAvatarUrl)
        put("savedAt", track.savedAt)
    }.toString()

    private fun bytes(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { stream -> DataOutputStream(stream).use(block) }.toByteArray()

    private fun syncSafe(value: Int) = byteArrayOf(
        ((value shr 21) and 127).toByte(), ((value shr 14) and 127).toByte(),
        ((value shr 7) and 127).toByte(), (value and 127).toByte(),
    )

    private fun existingMp3Frames(source: File, replaced: Set<String>): ByteArray {
        return source.inputStream().buffered().use { input ->
            val header = ByteArray(10)
            if (input.read(header) != 10 || String(header, 0, 3, Charsets.US_ASCII) != "ID3" ||
                header[3].toInt() !in 3..4 || header[5].toInt() != 0) return@use byteArrayOf()
            val length = (6..9).fold(0) { acc, i -> (acc shl 7) or (header[i].toInt() and 127) }
            check(length <= 32 * 1024 * 1024) { "ID3 metadata is too large" }
            val data = ByteArray(length)
            java.io.DataInputStream(input).readFully(data)
            val result = ByteArrayOutputStream()
            var offset = 0
            while (offset + 10 <= data.size && data[offset] != 0.toByte()) {
                val id = String(data, offset, 4, Charsets.US_ASCII)
                val size = if (header[3].toInt() == 4) (4..7).fold(0) { acc, i ->
                    (acc shl 7) or (data[offset + i].toInt() and 127)
                } else java.nio.ByteBuffer.wrap(data, offset + 4, 4).int
                if (size < 0 || offset + 10L + size > data.size) break
                if (id !in replaced && data[offset + 8] == 0.toByte() && data[offset + 9] == 0.toByte()) {
                    result.write(id.toByteArray(Charsets.US_ASCII))
                    result.write(syncSafe(size))
                    result.write(byteArrayOf(0, 0))
                    result.write(data, offset + 10, size)
                }
                offset += size + 10
            }
            result.toByteArray()
        }
    }

    private fun writeMp3(source: File, destination: File, track: OfflineTrack, cover: ByteArray?) {
        fun frame(id: String, data: ByteArray) = bytes {
            writeBytes(id)
            write(syncSafe(data.size))
            writeShort(0)
            write(data)
        }
        fun text(id: String, value: String?) = value?.let { frame(id, byteArrayOf(3) + it.toByteArray()) } ?: byteArrayOf()
        val picture = cover?.let {
            val mime = if (it.size > 2 && it[0] == 0xff.toByte() && it[1] == 0xd8.toByte()) "image/jpeg" else "image/png"
            frame("APIC", byteArrayOf(3) + mime.toByteArray() + byteArrayOf(0, 3, 0) + it)
        } ?: byteArrayOf()
        val replaced = mutableSetOf("TIT2", "TPE1", "TLEN")
        if (track.genre != null) replaced += "TCON"
        if (track.permalinkUrl != null) replaced += "WOAS"
        if (cover != null) replaced += "APIC"
        val tag = existingMp3Frames(source, replaced) + text("TIT2", track.title) + text("TPE1", track.username) +
            text("TCON", track.genre) + text("TLEN", track.durationMs.toString()) +
            frame("COMM", byteArrayOf(3) + "engResona".toByteArray() + byteArrayOf(0) + metadata(track).toByteArray()) +
            (track.permalinkUrl?.let { frame("WOAS", it.toByteArray()) } ?: byteArrayOf()) + picture
        destination.outputStream().buffered().use { out ->
            out.write(byteArrayOf(73, 68, 51, 4, 0, 0))
            out.write(syncSafe(tag.size))
            out.write(tag)
            source.inputStream().buffered().use { input ->
                input.mark(10)
                val header = ByteArray(10)
                if (input.read(header) == 10 && String(header, 0, 3, Charsets.US_ASCII) == "ID3") {
                    val size = (6..9).fold(0) { acc, index -> (acc shl 7) or (header[index].toInt() and 127) }
                    var remaining = size.toLong() + if (header[3] == 4.toByte() && header[5].toInt() and 16 != 0) 10 else 0
                    while (remaining > 0) {
                        val skipped = input.skip(remaining)
                        check(skipped > 0) { "Truncated ID3 tag" }
                        remaining -= skipped
                    }
                } else input.reset()
                input.copyTo(out)
            }
        }
    }

    private fun atom(type: String, data: ByteArray) = bytes {
        writeInt(data.size + 8)
        write(type.toByteArray(Charsets.ISO_8859_1))
        write(data)
    }

    private fun atoms(data: ByteArray): List<Pair<String, ByteArray>> {
        val result = mutableListOf<Pair<String, ByteArray>>()
        var offset = 0
        while (offset + 8 <= data.size) {
            val size = java.nio.ByteBuffer.wrap(data, offset, 4).int
            if (size < 8 || offset + size.toLong() > data.size) break
            val name = String(data, offset + 4, 4, Charsets.ISO_8859_1)
            result += name to data.copyOfRange(offset + 8, offset + size)
            offset += size
        }
        return result
    }

    private fun writeMp4(source: File, destination: File, track: OfflineTrack, cover: ByteArray?) {
        fun field(name: String, value: String?) = value?.let {
            atom(name, atom("data", bytes { writeInt(1); writeInt(0); write(it.toByteArray()) }))
        } ?: byteArrayOf()
        val artwork = cover?.let {
            atom("covr", atom("data", bytes {
                writeInt(if (it.size > 2 && it[0] == 0xff.toByte() && it[1] == 0xd8.toByte()) 13 else 14)
                writeInt(0)
                write(it)
            }))
        } ?: byteArrayOf()
        val fields = field("©nam", track.title) + field("©ART", track.username) + field("©gen", track.genre) +
            atom("----", atom("mean", byteArrayOf(0, 0, 0, 0) + "com.resona".toByteArray()) +
                atom("name", byteArrayOf(0, 0, 0, 0) + "source_metadata".toByteArray()) +
                atom("data", bytes { writeInt(1); writeInt(0); write(metadata(track).toByteArray()) })) + artwork
        val handler = atom("hdlr", bytes {
            writeInt(0); writeInt(0); writeBytes("mdir"); writeLong(0); writeInt(0); writeByte(0)
        })
        source.copyTo(destination, overwrite = true)
        RandomAccessFile(destination, "rw").use { file ->
            var offset = 0L
            var movie: ByteArray? = null
            while (offset + 8 <= source.length()) {
                file.seek(offset)
                val size = file.readInt().toLong() and 0xffffffffL
                val type = ByteArray(4).also { file.readFully(it) }.toString(Charsets.US_ASCII)
                val atomSize = if (size == 1L) file.readLong() else if (size == 0L) source.length() - offset else size
                check(atomSize >= 8 && offset + atomSize <= source.length()) { "Invalid MP4 atom" }
                if (type == "moov") {
                    check(size != 1L && atomSize <= 32 * 1024 * 1024) { "Unsupported MP4 metadata size" }
                    val data = ByteArray(atomSize.toInt() - 8)
                    file.readFully(data)
                    movie = data
                    file.seek(offset + 4)
                    file.writeBytes("free")
                    break
                }
                offset += atomSize
            }
            val original = movie ?: error("Missing MP4 movie metadata")
            val children = ByteArrayOutputStream()
            var position = 0
            while (position < original.size) {
                check(position + 8 <= original.size)
                val size = java.nio.ByteBuffer.wrap(original, position, 4).int
                check(size >= 8 && position + size <= original.size)
                val type = String(original, position + 4, 4, Charsets.US_ASCII)
                if (type != "udta") children.write(original, position, size)
                position += size
            }
            fun key(name: String, value: ByteArray): String = if (name != "----") name else {
                name + atoms(value).filter { it.first == "mean" || it.first == "name" }
                    .joinToString { it.second.toString(Charsets.UTF_8) }
            }
            val replaced = atoms(fields).map { key(it.first, it.second) }.toSet()
            val oldFields = atoms(original).filter { it.first == "udta" }.flatMap { atoms(it.second) }
                .filter { it.first == "meta" && it.second.size >= 4 }
                .flatMap { atoms(it.second.copyOfRange(4, it.second.size)) }
                .filter { it.first == "ilst" }.flatMap { atoms(it.second) }
                .filter { key(it.first, it.second) !in replaced }
                .fold(byteArrayOf()) { acc, (name, value) -> acc + atom(name, value) }
            val oldUserData = atoms(original).filter { it.first == "udta" }.flatMap { atoms(it.second) }
                .filter { it.first != "meta" }
                .fold(byteArrayOf()) { acc, (name, value) -> acc + atom(name, value) }
            val tags = atom("udta", oldUserData + atom("meta", byteArrayOf(0, 0, 0, 0) + handler + atom("ilst", oldFields + fields)))
            file.seek(file.length())
            file.write(atom("moov", children.toByteArray() + tags))
        }
    }
}
