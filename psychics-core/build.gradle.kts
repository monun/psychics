plugins {
    id("org.jetbrains.dokka") version "1.7.20"
}

dependencies {
    implementation("io.github.monun:kommand-api:3.1.7")
    implementation("io.github.monun:invfx-api:3.3.2")
}

tasks {
    processResources {
        filesMatching("**/*.yml") {
            expand(project.properties)
        }
    }

    register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }

    register<Jar>("dokkaJar") {
        archiveClassifier.set("javadoc")
        dependsOn("dokkaHtml")

        from("$buildDir/dokka/html/") {
            include("**")
        }
    }

    register<Jar>("paperJar") {
        archiveVersion.set("")
        archiveBaseName.set("Psychics")
        from(sourceSets["main"].output)

        doLast {
            copy {
                from(archiveFile)
                val plugins = File(rootDir, ".debug/plugins/")
                into(if (File(plugins, archiveFileName.get()).exists()) File(plugins, "update") else plugins)
            }
        }
    }
}
