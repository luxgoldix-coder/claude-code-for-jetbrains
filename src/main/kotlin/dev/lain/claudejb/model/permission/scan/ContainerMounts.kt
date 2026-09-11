package dev.lain.claudejb.model.permission.scan

internal object ContainerMounts {

    private val CONTAINER_VERBS = setOf("docker", "podman", "nerdctl", "kubectl", "oc")

    private val VOLUME_FLAGS = setOf("-v", "--volume")

    private val CONTAINER_SIDE_KEYS = listOf("target=", "destination=", "dst=")

    private val HOST_SIDE_KEYS = listOf("source=", "src=")

    private val WRITES_HOST = Regex(
        """\b(?:docker|podman|nerdctl)\b[^|;&\n]*(?:\s-v[\s=]|--volume\b|--mount\b)|\b(?:kubectl|oc)\s+cp\b""",
        RegexOption.IGNORE_CASE,
    )

    internal fun isContainerTool(verb: String?): Boolean = verb != null && verb in CONTAINER_VERBS

    internal fun writesHost(command: String): Boolean = WRITES_HOST.containsMatchIn(command)

    internal fun isMountKey(token: String): Boolean =
        (CONTAINER_SIDE_KEYS + HOST_SIDE_KEYS).any { token.startsWith(it, ignoreCase = true) }

    internal fun hostSide(previous: String?, token: String): String? {
        if (CONTAINER_SIDE_KEYS.any { token.startsWith(it, ignoreCase = true) }) return null
        HOST_SIDE_KEYS.firstOrNull { token.startsWith(it, ignoreCase = true) }?.let { return token.substring(it.length) }
        val spec = when {
            token.startsWith("--volume=") || token.startsWith("-v=") -> token.substringAfter('=')
            previous in VOLUME_FLAGS -> token
            else -> return token
        }
        val cut = mountSeparator(spec)
        if (cut < 0) return null
        return spec.substring(0, cut).ifEmpty { null }
    }

    private fun mountSeparator(spec: String): Int {
        val start = if (isDriveLetter(spec)) 2 else 0
        return spec.indexOf(':', start)
    }

    private fun isDriveLetter(spec: String): Boolean =
        spec.length > 2 && spec[1] == ':' && spec[0].isLetter() && (spec[2] == '/' || spec[2] == '\\')
}
