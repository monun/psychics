import org.gradle.kotlin.dsl.support.zipTo

val core = project(":${rootProject.name}-core")

subprojects {
    if (version == "unspecified") {
        version = rootProject.version
    }

    project.extra.set("abilityName", name.removePrefix("ability-"))

    dependencies {
        implementation(core)
    }

    tasks {
        processResources {
            filesMatching("**/*.yml") {
                expand(project.properties)
            }
        }

        register<Jar>("paperJar") {
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
    }
}

gradle.buildFinished {
    val libs = File(buildDir, "libs")

    if (libs.exists())
        zipTo(File(buildDir, "abilities.zip"), libs)
}