package com.savoo.scclient.compat

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

actual class SharedPreferencesImpl private constructor(private val name: String) : SharedPreferences {
    private val defaults = NSUserDefaults(suiteName = "prefs.$name")

    actual override fun getString(key: String, defValue: String?): String? = defaults.stringForKey(key) ?: defValue

    actual override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (defaults.arrayForKey(key) as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: defValues

    actual override fun getInt(key: String, defValue: Int): Int = number(key)?.intValue ?: defValue
    actual override fun getLong(key: String, defValue: Long): Long = number(key)?.longLongValue ?: defValue
    actual override fun getFloat(key: String, defValue: Float): Float = number(key)?.floatValue ?: defValue
    actual override fun getBoolean(key: String, defValue: Boolean): Boolean = number(key)?.boolValue ?: defValue
    actual override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    actual override fun getAll(): Map<String, *> =
        defaults.dictionaryRepresentation().entries
            .filter { (k, _) -> k is String && keys().contains(k) }
            .associate { (k, v) -> k as String to v }

    actual override fun edit(): SharedPreferences.Editor = EditorImpl()

    private fun number(key: String): NSNumber? = defaults.objectForKey(key) as? NSNumber

    private fun keys(): Set<String> = (defaults.arrayForKey(KEYS) as? List<*>)?.mapNotNull { it as? String }?.toSet().orEmpty()

    private inner class EditorImpl : SharedPreferences.Editor {
        private val ops = ArrayList<() -> Unit>()
        private var clear = false

        override fun putString(key: String, value: String?) = op(key) {
            if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
        }
        override fun putStringSet(key: String, values: Set<String>?) = op(key) {
            if (values == null) defaults.removeObjectForKey(key) else defaults.setObject(values.toList(), key)
        }
        override fun putInt(key: String, value: Int) = op(key) { defaults.setInteger(value.toLong(), key) }
        override fun putLong(key: String, value: Long) = op(key) { defaults.setObject(NSNumber(longLong = value), key) }
        override fun putFloat(key: String, value: Float) = op(key) { defaults.setFloat(value, key) }
        override fun putBoolean(key: String, value: Boolean) = op(key) { defaults.setBool(value, key) }
        override fun remove(key: String) = apply { ops.add { defaults.removeObjectForKey(key) } }
        override fun clear() = apply { clear = true }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean {
            if (clear) {
                keys().forEach { defaults.removeObjectForKey(it) }
                defaults.removeObjectForKey(KEYS)
            }
            ops.forEach { it() }
            return true
        }

        private fun op(key: String, block: () -> Unit): SharedPreferences.Editor = apply {
            ops.add {
                block()
                val all = keys()
                if (key !in all) defaults.setObject((all + key).toList(), KEYS)
            }
        }
    }

    actual companion object {
        private const val KEYS = "__keys__"
        private val cache = HashMap<String, SharedPreferences>()

        actual fun get(name: String): SharedPreferences = cache.getOrPut(name) { SharedPreferencesImpl(name) }
    }
}
