rootProject.name = "psychics"

val core = "${rootProject.name}-core"
val abilities = "${rootProject.name}-abilities"

include(core, abilities)

file(abilities).listFiles()?.filter { it.isDirectory && it.name.startsWith("ability-") }?.forEach { file ->
    include(":psychics-abilities:${file.name}")
}
