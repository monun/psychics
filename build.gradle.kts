plugins {
    kotlin("jvm") version "1.6.10"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

allprojects {
    repositories {
        mavenCentral()
    }

    tasks {
        withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class) {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        maven("https://papermc.io/repo/repository/maven-public/")
        // maven("https://papermc.io/repo/repository/maven-snapshots/")
        maven("https://oss.sonatype.org/content/groups/public/")
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT") // 왜 안돼?

        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))

        implementation("io.github.monun:tap-api:4.4.0") // 왜 안돼? 22

        testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
        testImplementation("org.mockito:mockito-core:3.6.28")
    }
}
