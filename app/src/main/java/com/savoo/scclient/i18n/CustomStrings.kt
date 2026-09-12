package com.savoo.scclient.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.Xml
import com.savoo.scclient.debug.DebugLog
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

private const val TAG = "CustomStrings"
private const val FILE_NAME = "custom_strings.xml"
private const val PREFS_NAME = "sc_settings"
private const val LANGUAGE_KEY = "language"
private const val CUSTOM_LANGUAGE = "CUSTOM"

data class CustomStringsStats(val applied: Int, val unknown: Int, val total: Int)

class CustomTranslation(
    val strings: Map<Int, String>,
    val arrays: Map<Int, Array<String>>,
) {
    val isEmpty: Boolean get() = strings.isEmpty() && arrays.isEmpty()

    companion object {
        val EMPTY = CustomTranslation(emptyMap(), emptyMap())
    }
}

object CustomStrings {

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

    fun install(context: Context, input: InputStream): Result<CustomStringsStats> = runCatching {
        val target = file(context)
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        translation = null
        translationFor(context)
        stats ?: CustomStringsStats(0, 0, 0)
    }

    fun clear(context: Context) {
        file(context).delete()
        translation = null
        stats = null
    }

    private fun parse(context: Context): CustomTranslation {
        val source = file(context)
        if (!source.exists()) {
            stats = null
            return CustomTranslation.EMPTY
        }
        val strings = HashMap<Int, String>()
        val arrays = HashMap<Int, Array<String>>()
        var unknown = 0
        var total = 0
        runCatching {
            source.inputStream().use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, null)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "string" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val value = runCatching { parser.nextText() }.getOrNull()
                                if (!name.isNullOrBlank() && value != null) {
                                    total++
                                    val id = context.resources
                                        .getIdentifier(name, "string", context.packageName)
                                    if (id != 0) strings[id] = unescape(value) else unknown++
                                }
                            }
                            "string-array" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val items = readArrayItems(parser)
                                if (!name.isNullOrBlank() && items.isNotEmpty()) {
                                    total++
                                    val id = context.resources
                                        .getIdentifier(name, "array", context.packageName)
                                    if (id != 0) arrays[id] = items.toTypedArray() else unknown++
                                }
                            }
                        }
                    }
                    event = parser.next()
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

    private fun readArrayItems(parser: XmlPullParser): List<String> {
        val items = ArrayList<String>()
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.name == "string-array")
        ) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                runCatching { parser.nextText() }.getOrNull()?.let { items.add(unescape(it)) }
            }
            event = parser.next()
        }
        return items
    }

    private fun unescape(raw: String): String = raw
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
}

@Suppress("DEPRECATION")
class CustomStringsResources(
    base: Resources,
    private val translation: CustomTranslation,
) : Resources(base.assets, base.displayMetrics, base.configuration) {

    override fun getString(id: Int): String = translation.strings[id] ?: super.getString(id)

    // A user-supplied string can carry the wrong placeholders (%d where the original has %s), which
    // would throw mid-render - fall back to the packaged string instead of taking the screen down.
    override fun getString(id: Int, vararg formatArgs: Any?): String {
        val custom = translation.strings[id] ?: return super.getString(id, *formatArgs)
        return runCatching { String.format(configuration.locales[0], custom, *formatArgs) }
            .getOrElse { super.getString(id, *formatArgs) }
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
    val translation = CustomStrings.translationFor(this)
    if (translation.isEmpty) return this
    return CustomStringsContextWrapper(this, CustomStringsResources(resources, translation))
}
