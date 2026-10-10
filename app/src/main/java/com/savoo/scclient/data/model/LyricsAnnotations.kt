package com.savoo.scclient.data.model

data class GeniusAnnotation(
    val id: Long,
    val fragment: String,
    val body: String,
    val votes: Int,
    val verified: Boolean,
    val reviewed: Boolean,
    val url: String?,
)

object LyricsAnnotationMatcher {
    private const val MAX_FRAGMENT_LINES = 2
    private const val MIN_MATCH_LENGTH = 6

    fun match(lines: List<String>, annotations: List<GeniusAnnotation>): Map<Int, List<GeniusAnnotation>> {
        if (lines.isEmpty() || annotations.isEmpty()) return emptyMap()
        val normalizedLines = lines.map(::normalize)
        val result = mutableMapOf<Int, MutableList<GeniusAnnotation>>()
        for (annotation in annotations) {
            val fragmentLines = annotation.fragment.lines()
                .filterNot { it.trim().startsWith("[") }
                .map(::normalize)
                .filter { it.isNotEmpty() }
            if (fragmentLines.isEmpty() || fragmentLines.size > MAX_FRAGMENT_LINES) continue
            for (fragment in fragmentLines) {
                if (fragment.length < MIN_MATCH_LENGTH) continue
                normalizedLines.forEachIndexed { index, line ->
                    if (line.isEmpty()) return@forEachIndexed
                    val hit = line.contains(fragment) || (line.length >= MIN_MATCH_LENGTH && fragment.contains(line))
                    if (hit) {
                        val list = result.getOrPut(index) { mutableListOf() }
                        if (list.none { it.id == annotation.id }) list += annotation
                    }
                }
            }
        }
        return result
    }

    fun normalize(text: String): String =
        text.lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), " ")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}
