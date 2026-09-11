package dev.lain.claudejb.model.settings

internal object IdeRulesAdoption {

    fun adopt(s: ClaudeSettings.State) {
        if (adopted(s.ideMcp.rules) != s.ideMcp.rules) s.ideMcp.rules = LaunchDefaults.DEFAULT_IDE_RULES
    }

    fun adopted(csv: String): String {
        val known = LaunchDefaults.DEFAULT_IDE_RULES.split(',').toSet()
        val stored = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return if (stored.any { it !in known }) LaunchDefaults.DEFAULT_IDE_RULES else csv
    }
}
