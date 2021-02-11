import org.gradle.kotlin.dsl.support.zipTo

subprojects {
    if (version == "unspecified") version = parent!!.version

    dependencies {
        compileOnly(project(":psychics-common"))
    }

    tasks {
        shadowJar {
            archiveBaseName.set("${project.group}.${project.name}")
        }
        create<Copy>("copyToParent") {
            from(shadowJar)
            into { File(parent!!.buildDir, "libs") }
        }
        assemble {
            dependsOn(named("copyToParent"))
        }
        create<Copy>("copyToServer") {
            from(shadowJar)
            var dest = File(rootDir, ".server/plugins/Psychics/abilities/")
            // if plugin.jar exists in plugins change dest to plugins/update
            if (File(dest, shadowJar.get().archiveFileName.get()).exists()) dest = File(dest, "update")
            into(dest)
        }
    }
}

tasks.filter { it.name != "clean" }.forEach { it.enabled = false }

gradle.buildFinished {
    val libs = File(buildDir, "libs")

    if (libs.exists())
        zipTo(File(buildDir, "abilities.zip"), libs)
}