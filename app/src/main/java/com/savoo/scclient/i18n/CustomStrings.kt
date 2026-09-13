package com.savoo.scclient.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.Xml
import com.savoo.scclient.debug.CrashReporter
import com.savoo.scclient.debug.DebugLog
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

private const val TAG = "CustomStrings"
private const val FILE_NAME = "custom_strings.xml"
private const val PREFS_NAME = "sc_settings"
private const val LANGUAGE_KEY = "language"
private const val CUSTOM_LANGUAGE = "CUSTOM"
private const val DISABLED_FILE_NAME = "custom_strings.disabled.xml"
private const val SAFE_MODE_NOTICE_KEY = "custom_strings_safe_mode_notice"
private const val SAFE_MODE_CRASH_STREAK = 2

data class CustomStringsStats(val applied: Int, val unknown: Int, val total: Int)

enum class CustomStringsDisableReason { CRASHES, TOO_LARGE }

class CustomStringsTooLargeException : IllegalStateException("Custom translation is larger than the limit")

class CustomStringsCheck internal constructor(
    internal val bytes: ByteArray,
    val total: Int,
    val unknown: Int,
    val longStrings: Int,
    val brokenPlaceholders: Int,
) {
    val sizeBytes: Long get() = bytes.size.toLong()
    val hasWarnings: Boolean get() = longStrings > 0 || brokenPlaceholders > 0
}

class CustomTranslation(
    val strings: Map<Int, String>,
    val arrays: Map<Int, Array<String>>,
) {
    val isEmpty: Boolean get() = strings.isEmpty() && arrays.isEmpty()

    companion object {
        val EMPTY = CustomTranslation(emptyMap(), emptyMap())
    }
}

private sealed interface RawEntry {
    val name: String

    data class Str(override val name: String, val raw: String) : RawEntry
    data class Arr(override val name: String, val items: List<String>) : RawEntry
}

object CustomStrings {

    const val MAX_FILE_BYTES = 3 * 1024 * 1024
    const val MAX_STRING_CHARS = 4000
    private const val MAX_LENGTH_RATIO = 4
    private const val MAX_LENGTH_SLACK = 100

    private val FORMAT_SPEC = Regex("%(\\d+\\$)?[-#+ 0,(<]*\\d*(\\.\\d+)?([tT][a-zA-Z]|[bBhHsScCdoxXeEfgGaA%n])")

    @Volatile
    private var translation: CustomTranslation? = null

    @Volatile
    var stats: CustomStringsStats? = null
        private set

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun hasFile(context: Context): Boolean = file(context).exists()

    fun isSelected(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, null) == CUSTOM_LANGUAGE

    fun translationFor(context: Context): CustomTranslation {
        translation?.let { return it }
        val parsed = parse(context)
        translation = parsed
        return parsed
    }

    fun inspect(context: Context, input: InputStream): Result<CustomStringsCheck> = runCatching {
        val bytes = input.use { readLimited(it) }
        var total = 0
        var unknown = 0
        var longStrings = 0
        var broken = 0
        readEntries(ByteArrayInputStream(bytes)) { entry ->
            total++
            val id = resolveId(context, entry)
            if (id == 0) {
                unknown++
                return@readEntries
            }
            when {
                isTooLong(context, id, entry) -> longStrings++
                entry is RawEntry.Str && hasBrokenPlaceholders(context.resources.getString(id), unescape(entry.raw)) -> broken++
            }
        }
        CustomStringsCheck(bytes, total, unknown, longStrings, broken)
    }

    fun install(context: Context, check: CustomStringsCheck, skipProblems: Boolean): Result<CustomStringsStats> =
        runCatching {
            val bytes = if (skipProblems) sanitize(context, check.bytes) else check.bytes
            file(context).writeBytes(bytes)
            translation = null
            translationFor(context)
            stats ?: CustomStringsStats(0, 0, 0)
        }

    fun enterSafeModeIfCrashLooping(context: Context): Boolean {
        if (CrashReporter.launchCrashStreak(context) < SAFE_MODE_CRASH_STREAK) return false
        if (!file(context).exists()) return false
        CrashReporter.resetLaunchCrashStreak(context)
        disable(context, CustomStringsDisableReason.CRASHES)
        return true
    }

    fun consumeDisableNotice(context: Context): CustomStringsDisableReason? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val reason = prefs.getString(SAFE_MODE_NOTICE_KEY, null) ?: return null
        prefs.edit().remove(SAFE_MODE_NOTICE_KEY).apply()
        return runCatching { CustomStringsDisableReason.valueOf(reason) }.getOrNull()
    }

    fun clear(context: Context) {
        file(context).delete()
        translation = null
        stats = null
    }

    private fun disable(context: Context, reason: CustomStringsDisableReason) {
        val source = file(context)
        val quarantined = File(context.filesDir, DISABLED_FILE_NAME)
        quarantined.delete()
        if (reason == CustomStringsDisableReason.TOO_LARGE || !source.renameTo(quarantined)) source.delete()
        translation = null
        stats = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(SAFE_MODE_NOTICE_KEY, reason.name).commit()
        DebugLog.log(TAG, "custom translation disabled: $reason")
    }

    private fun parse(context: Context): CustomTranslation {
        val source = file(context)
        if (!source.exists()) {
            stats = null
            return CustomTranslation.EMPTY
        }
        if (source.length() > MAX_FILE_BYTES) {
            disable(context, CustomStringsDisableReason.TOO_LARGE)
            return CustomTranslation.EMPTY
        }
        val strings = HashMap<Int, String>()
        val arrays = HashMap<Int, Array<String>>()
        var unknown = 0
        var total = 0
        runCatching {
            source.inputStream().use { stream ->
                readEntries(stream) { entry ->
                    total++
                    val id = resolveId(context, entry)
                    when {
                        id == 0 -> unknown++
                        entry is RawEntry.Str -> strings[id] = unescape(entry.raw)
                        entry is RawEntry.Arr -> arrays[id] = entry.items.map(::unescape).toTypedArray()
                    }
                }
            }
        }.onFailure {
            DebugLog.log(TAG, "parse failed: $it")
            stats = null
            return CustomTranslation.EMPTY
        }
        stats = CustomStringsStats(applied = strings.size + arrays.size, unknown = unknown, total = total)
        DebugLog.log(TAG, "loaded ${strings.size} strings, ${arrays.size} arrays, $unknown unknown of $total")
        return CustomTranslation(strings, arrays)
    }

    private fun sanitize(context: Context, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val serializer = Xml.newSerializer()
        serializer.setOutput(out, "UTF-8")
        serializer.startDocument("UTF-8", null)
        serializer.startTag(null, "resources")
        readEntries(ByteArrayInputStream(bytes)) { entry ->
            val id = resolveId(context, entry)
            if (id == 0 || isTooLong(context, id, entry)) return@readEntries
            when (entry) {
                is RawEntry.Str -> {
                    if (hasBrokenPlaceholders(context.resources.getString(id), unescape(entry.raw))) return@readEntries
                    serializer.startTag(null, "string")
                    serializer.attribute(null, "name", entry.name)
                    serializer.text(entry.raw)
                    serializer.endTag(null, "string")
                }
                is RawEntry.Arr -> {
                    serializer.startTag(null, "string-array")
                    serializer.attribute(null, "name", entry.name)
                    entry.items.forEach {
                        serializer.startTag(null, "item")
                        serializer.text(it)
                        serializer.endTag(null, "item")
                    }
                    serializer.endTag(null, "string-array")
                }
            }
        }
        serializer.endTag(null, "resources")
        serializer.endDocument()
        return out.toByteArray()
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > MAX_FILE_BYTES) throw CustomStringsTooLargeException()
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun readEntries(stream: InputStream, onEntry: (RawEntry) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "string" -> {
                        val name = parser.getAttributeValue(null, "name")
                        val value = runCatching { parser.nextText() }.getOrNull()
                        if (!name.isNullOrBlank() && value != null) onEntry(RawEntry.Str(name, value))
                    }
                    "string-array" -> {
                        val name = parser.getAttributeValue(null, "name")
                        val items = readArrayItems(parser)
                        if (!name.isNullOrBlank() && items.isNotEmpty()) onEntry(RawEntry.Arr(name, items))
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun readArrayItems(parser: XmlPullParser): List<String> {
        val items = ArrayList<String>()
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.name == "string-array")
        ) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                runCatching { parser.nextText() }.getOrNull()?.let { items.add(it) }
            }
            event = parser.next()
        }
        return items
    }

    private fun resolveId(context: Context, entry: RawEntry): Int {
        val type = if (entry is RawEntry.Str) "string" else "array"
        return context.resources.getIdentifier(entry.name, type, context.packageName)
    }

    private fun isTooLong(context: Context, id: Int, entry: RawEntry): Boolean = when (entry) {
        is RawEntry.Str -> isTooLong(context.resources.getString(id), unescape(entry.raw))
        is RawEntry.Arr -> {
            val originals = runCatching { context.resources.getStringArray(id) }.getOrNull()
            entry.items.withIndex().any { (index, item) ->
                isTooLong(originals?.getOrNull(index).orEmpty(), unescape(item))
            }
        }
    }

    private fun isTooLong(original: String, custom: String): Boolean =
        custom.length > MAX_STRING_CHARS || custom.length > original.length * MAX_LENGTH_RATIO + MAX_LENGTH_SLACK

    private fun conversions(text: String): List<Char> =
        FORMAT_SPEC.findAll(text)
            .map { it.value.last().lowercaseChar() }
            .filter { it != '%' && it != 'n' }
            .sorted()
            .toList()

    private fun hasBrokenPlaceholders(original: String, custom: String): Boolean {
        val expected = conversions(original)
        if (expected.isEmpty()) return false
        if (FORMAT_SPEC.replace(custom, "").contains('%')) return true
        val remaining = expected.toMutableList()
        return conversions(custom).any { !remaining.remove(it) }
    }

    private fun unescape(raw: String): String {
        if (!raw.contains('\\')) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i == raw.lastIndex) {
                out.append(c)
                i++
                continue
            }
            when (val next = raw[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'u' -> {
                    val code = raw.substring(i + 2, minOf(i + 6, raw.length))
                        .takeIf { it.length == 4 }
                        ?.toIntOrNull(16)
                    if (code != null) {
                        out.append(code.toChar())
                        i += 6
                        continue
                    }
                    out.append(next)
                }
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }
}

@Suppress("DEPRECATION")
class CustomStringsResources(
    private val base: Resources,
    private val translation: CustomTranslation,
) : Resources(base.assets, base.displayMetrics, base.configuration) {

    override fun getString(id: Int): String = translation.strings[id] ?: super.getString(id)

    override fun getString(id: Int, vararg formatArgs: Any?): String {
        val custom = translation.strings[id] ?: return base.getString(id, *formatArgs)
        return runCatching { String.format(configuration.locales[0], custom, *formatArgs) }
            .getOrElse { base.getString(id, *formatArgs) }
    }

    override fun getText(id: Int): CharSequence = translation.strings[id] ?: super.getText(id)

    override fun getText(id: Int, def: CharSequence?): CharSequence =
        translation.strings[id] ?: super.getText(id, def)

    override fun getStringArray(id: Int): Array<String> =
        translation.arrays[id] ?: super.getStringArray(id)

    override fun getTextArray(id: Int): Array<CharSequence> =
        translation.arrays[id]?.map { it as CharSequence }?.toTypedArray() ?: super.getTextArray(id)
}

private class CustomStringsContextWrapper(
    base: Context,
    private val wrapped: Resources,
) : ContextWrapper(base) {
    override fun getResources(): Resources = wrapped
}

fun Context.withCustomStrings(): Context {
    if (!CustomStrings.isSelected(this)) return this
    if (CustomStrings.enterSafeModeIfCrashLooping(this)) return this
    val translation = CustomStrings.translationFor(this)
    if (translation.isEmpty) return this
    return CustomStringsContextWrapper(this, CustomStringsResources(resources, translation))
}
