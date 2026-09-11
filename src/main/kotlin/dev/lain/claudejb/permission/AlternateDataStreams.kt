package dev.lain.claudejb.permission

object AlternateDataStreams {

    private val EXPLICIT_STREAM = Regex(""":\x24DATA(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)

    internal fun streamHit(paths: List<String>): String? = paths.firstOrNull { EXPLICIT_STREAM.containsMatchIn(it) }

    fun hasStream(path: String): Boolean = EXPLICIT_STREAM.containsMatchIn(path)
}
