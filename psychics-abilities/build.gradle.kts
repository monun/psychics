import org.gradle.kotlin.dsl.support.zipTo

val core = project(":${rootProject.name}-core")

subprojects {
    if (version == "unspecified") {
        version = rootProject.version
    }

    val projectName = name.removePrefix("ability-")

    project.extra.apply {
        set("projectName", projectName)
        set("packageName", name.removePrefix("ability-").replace("-", ""))
        set("abilityName", name.removePrefix("ability-").split('-').joinToString(separator = "") { it.capitalize() })
    }

    dependencies {
        implementation(core)
    }

    tasks {
        processResources {
            filesMatching("**/*.yml") {
                expand(project.properties)
            }
        }

        val paperJar = register<Jar>("paperJar") {
            archiveVersion.set("")
            archiveBaseName.set("${project.group}.${project.name.removePrefix("ability-")}")
            from(sourceSets["main"].output)

            doLast {
                copy {
                    from(archiveFile)
                    val plugins = File(rootDir, ".debug/plugins/Psychics/abilities")
                    into(if (File(plugins, archiveFileName.get()).exists()) File(plugins, "update") else plugins)
                }
            }
        }

        rootProject.tasks {
            register<DefaultTask>(projectName) {
                dependsOn(paperJar)
            }
        }
    }
}

gradle.buildFinished {
    val abilitiesDir = File(buildDir,"abilities")
    abilitiesDir.mkdir()

    subprojects
        .map { File(it.buildDir, "libs") }
        .filter { it.exists() }
        .mapNotNull { it.listFiles()!!.singleOrNull() }
        .forEach {
            val newFile = File(abilitiesDir,it.name)
            it.copyTo(newFile,true)
        }

    zipTo(File(buildDir, "abilities.zip"), abilitiesDir)
}