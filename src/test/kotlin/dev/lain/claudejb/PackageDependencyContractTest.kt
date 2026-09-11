package dev.lain.claudejb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PackageDependencyContractTest {

    private val imports: List<Import> = MainSources.files().flatMap { file ->
        val from = packageOf(file)
        MainSources.codeOf(file)
            .map { it.trim() }
            .filter { it.startsWith(IMPORT_PREFIX) }
            .map { line -> Import(file, from, line.removePrefix(IMPORT_PREFIX).substringBefore(" as ").trim()) }
    }

    @Test
    fun `the scan sees the whole tree`() {
        assertTrue(MainSources.files().size >= MIN_SOURCES) { "only ${MainSources.files().size} sources found" }
        assertTrue(imports.size >= MIN_IMPORTS) { "only ${imports.size} in-repo imports found" }
    }

    @Test
    fun `every package imports only the layers below it`() {
        val offenders = imports
            .filter { it.to !in ALLOWED.getValue(it.from) && it.to != it.from }
            .map { "${it.file.relativeTo(MainSources.root(SOURCE_ROOT))}: ${it.from} -> ${it.to}" }
        assertEquals(emptyList<String>(), offenders) {
            "A package reaches above its layer. session/ never imports ui/, settings/ never imports session/, " +
                "and permission/ and protocol/ stay below both: the direction is the architecture."
        }
    }

    @Test
    fun `the wire and the bridge know nothing of the platform`() {
        val offenders = MainSources.files()
            .filter { file -> packageOf(file) in PLATFORM_FREE || file.name in PLATFORM_FREE_FILES }
            .flatMap { file ->
                MainSources.codeOf(file).map { it.trim() }
                    .filter { line -> PLATFORM_IMPORTS.any { line.startsWith("import $it") } }
                    .map { "${file.name}: $it" }
            }
        assertEquals(emptyList<String>(), offenders) {
            "protocol/, permission/ and the pure bridge unit-test on a plain JVM; a platform import there " +
                "drags the IDE into every test that touches them."
        }
    }

    private fun packageOf(file: File): String {
        val rel = file.relativeTo(MainSources.root(SOURCE_ROOT)).invariantSeparatorsPath
        return rel.removePrefix("$PACKAGE_ROOT/").substringBeforeLast('/', "")
    }

    private data class Import(val file: File, val from: String, val to: String) {
        constructor(file: File, from: String, fqn: String, unused: Unit = Unit) :
            this(file, from, packageOfFqn(fqn))
    }

    private companion object {

        const val SOURCE_ROOT = "src/main/kotlin"
        const val PACKAGE_ROOT = "dev/lain/claudejb"
        const val IMPORT_PREFIX = "import dev.lain.claudejb."
        const val MIN_SOURCES = 100
        const val MIN_IMPORTS = 300

        fun packageOfFqn(fqn: String): String {
            val segments = fqn.split('.')
            val packageSegments = segments.dropLast(1).takeWhile { it.first().isLowerCase() }
            return packageSegments.joinToString("/")
        }

        val ALLOWED: Map<String, Set<String>> = mapOf(
            "util" to setOf(),
            "protocol" to setOf("util"),
            "diff" to setOf("protocol", "util"),
            "context" to setOf("diff", "protocol", "util"),
            "git" to setOf("diff", "util"),
            "permission" to setOf("protocol", "diff", "util"),
            "settings" to setOf("permission", "protocol", "diff", "util"),
            "process" to setOf("settings", "protocol", "util"),
            "vuln" to setOf("settings", "protocol", "util"),
            "session" to setOf("vuln", "process", "settings", "permission", "git", "context", "diff", "protocol", "util"),
            "ui" to setOf(
                "ui/jcef", "session", "vuln", "process", "settings", "permission", "git", "context", "diff",
                "protocol", "util",
            ),
            "ui/jcef" to setOf(
                "ui", "session", "vuln", "process", "settings", "permission", "git", "context", "diff",
                "protocol", "util",
            ),
            "actions" to setOf(
                "ui", "ui/jcef", "session", "vuln", "process", "settings", "permission", "git", "context", "diff",
                "protocol", "util",
            ),
        )

        val PLATFORM_FREE = setOf("protocol", "permission")

        val PLATFORM_FREE_FILES = setOf("JcefBridge.kt", "Msg.kt")

        val PLATFORM_IMPORTS = listOf("com.intellij", "org.cef", "java.awt", "javax.swing")
    }
}
