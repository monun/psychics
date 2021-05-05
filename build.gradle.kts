import java.io.OutputStream

plugins {
    kotlin("jvm") version "1.4.32"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    `maven-publish`
}

val relocate = (findProperty("relocate") as? String)?.toBoolean() ?: true

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.github.johnrengelman.shadow")

    if (project == project(":psychics-abilities")) return@subprojects

    repositories {
        maven(url = "https://papermc.io/repo/repository/maven-public/")
        maven(url = "https://repo.dmulloy2.net/nexus/repository/public/")
        maven(url = "https://jitpack.io/")
        mavenLocal()
    }

    // implementation only :psychics-common project
    fun DependencyHandlerScope.implementationOnlyCommon(dependencyNotation: Any): Dependency? {
        return if (this@subprojects == this@subprojects.project(":psychics-common"))
            implementation(dependencyNotation)
        else
            compileOnly(dependencyNotation)
    }

    dependencies {
        compileOnly(kotlin("stdlib"))
        compileOnly(kotlin("reflect"))
        compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
        compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
        compileOnly("com.comphenix.protocol:ProtocolLib:4.6.0")
        compileOnly("com.github.monun:invfx:1.4.3")

        implementationOnlyCommon("com.github.monun:tap:3.4.9")
        implementationOnlyCommon("com.github.monun:kommand:0.9.0")

        testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
        testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.0")
        testImplementation("org.mockito:mockito-core:3.6.28")
        testImplementation("org.spigotmc:spigot:1.16.5-R0.1-SNAPSHOT")

        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
    }

    tasks {
        withType<Test>().configureEach {
            useJUnitPlatform()
        }
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "11"
        }
        processResources {
            filesMatching("**/*.yml") {
                expand(project.properties)
            }
        }
        shadowJar {
            archiveClassifier.set("")
            archiveVersion.set("")

            if (relocate) {
                relocate("com.github.monun.tap", "com.github.monun.psychics.tap")
                relocate("com.github.monun.kommand", "com.github.monun.psychics.kommand")
            }
        }
        assemble {
            dependsOn(shadowJar)
        }
    }
}

project(":psychics-common") {
    apply(plugin = "maven-publish")
}

tasks {
    create<DefaultTask>("setupWorkspace") {
        doLast {
            val versions = arrayOf(
                "1.16.5"
            )
            val buildtoolsDir = file(".buildtools")
            val buildtools = File(buildtoolsDir, "BuildTools.jar")

            val maven = File(System.getProperty("user.home"), ".m2/repository/org/spigotmc/spigot/")
            val repos = maven.listFiles { file: File -> file.isDirectory } ?: emptyArray()
            val missingVersions = versions.filter { version ->
                repos.find { it.name.startsWith(version) }?.also { println("Skip downloading spigot-$version") } == null
            }.also { if (it.isEmpty()) return@doLast }

            val download by registering(de.undercouch.gradle.tasks.download.Download::class) {
                src("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar")
                dest(buildtools)
            }
            download.get().download()

            runCatching {
                for (v in missingVersions) {
                    println("Downloading spigot-$v...")

                    javaexec {
                        workingDir(buildtoolsDir)
                        main = "-jar"
                        args = listOf("./${buildtools.name}", "--rev", v)
                        // Silent
                        standardOutput = OutputStream.nullOutputStream()
                        errorOutput = OutputStream.nullOutputStream()
                    }
                }
            }.onFailure {
                it.printStackTrace()
            }
            buildtoolsDir.deleteRecursively()
        }
    }
}