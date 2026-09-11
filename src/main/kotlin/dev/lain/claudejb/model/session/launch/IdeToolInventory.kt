package dev.lain.claudejb.model.session.launch

object IdeToolInventory {

    fun fromToolNames(names: Collection<String>): Set<String> =
        names.mapNotNull { name -> IdeServer.ofToolName(name)?.let { name.removePrefix(it.toolPrefix) } }.toSet()

    fun csv(tools: Set<String>): String = tools.sorted().joinToString(",")

    fun parse(csv: String): Set<String> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
