plugins {
    id("org.jetbrains.dokka") version "1.5.0"
}

dependencies {
    implementation("io.github.monun:kommand-api:2.6.6")
    implementation("io.github.monun:invfx-api:3.0.1")
    implementation(kotlin("script-runtime"))
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
