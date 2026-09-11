package dev.lain.claudejb.settings

import dev.lain.claudejb.permission.SecurityCategory
import dev.lain.claudejb.permission.SecurityRule

object GuardWhitelists {

    enum class Listed { RULE, CATEGORY, EVERYWHERE }

    fun all(state: ClaudeSettings.State, rule: SecurityRule): List<String> =
        byRule(state.securityRuleWhitelists)[rule].orEmpty().toList() +
            byCategory(state.securityCategoryWhitelists)[rule.category].orEmpty() +
            commands(state.securityCommandWhitelist)

    fun add(state: ClaudeSettings.State, rule: SecurityRule, command: String) {
        state.securityRuleWhitelists = withEntry(state.securityRuleWhitelists, rule.name, command)
    }

    fun listedIn(state: ClaudeSettings.State, rule: SecurityRule, same: (String) -> Boolean): Listed? = when {
        holds(state.securityRuleWhitelists, rule.name, same) -> Listed.RULE
        holds(state.securityCategoryWhitelists, rule.category.name, same) -> Listed.CATEGORY
        holds(state.securityCommandWhitelist, null, same) -> Listed.EVERYWHERE
        else -> null
    }

    fun remove(state: ClaudeSettings.State, rule: SecurityRule, from: Listed, same: (String) -> Boolean) {
        when (from) {
            Listed.RULE -> state.securityRuleWhitelists = without(state.securityRuleWhitelists, rule.name, same)

            Listed.CATEGORY ->
                state.securityCategoryWhitelists = without(state.securityCategoryWhitelists, rule.category.name, same)

            Listed.EVERYWHERE -> state.securityCommandWhitelist = without(state.securityCommandWhitelist, null, same)
        }
    }

    fun commands(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }

    fun byRule(text: String): Map<SecurityRule, Set<String>> = keyed(text) { SecurityRule.from(it) }

    fun byCategory(text: String): Map<SecurityCategory, Set<String>> =
        keyed(text) { name -> SecurityCategory.entries.firstOrNull { it.name == name } }

    fun withEntry(text: String, key: String, command: String): String {
        val wanted = command.trim()
        if (wanted.isEmpty()) return text
        val line = "$key=$wanted"
        if (entries(text).any { it == line }) return text
        return if (text.isBlank()) line else text.trimEnd() + "\n" + line
    }

    fun holds(text: String, key: String?, same: (String) -> Boolean): Boolean =
        entries(text).any { matches(it, key, same) }

    fun without(text: String, key: String?, same: (String) -> Boolean): String =
        entries(text).filterNot { matches(it, key, same) }.joinToString("\n")

    private fun matches(entry: String, key: String?, same: (String) -> Boolean): Boolean {
        if (key == null) return same(entry)
        return entry.substringBefore('=', "").trim() == key && same(entry.substringAfter('=', "").trim())
    }

    private fun entries(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }

    private fun <K> keyed(text: String, resolve: (String) -> K?): Map<K, Set<String>> {
        val out = LinkedHashMap<K, MutableSet<String>>()
        entries(text).forEach { entry ->
            val command = entry.substringAfter('=', "").trim()
            if (command.isEmpty()) return@forEach
            val key = resolve(entry.substringBefore('=', "").trim()) ?: return@forEach
            out.getOrPut(key) { LinkedHashSet() }.add(command)
        }
        return out
    }
}
