tasks {
    shadowJar {
        archiveBaseName.set("Psychics")
    }
    create<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }
    create<Copy>("paper") {
        from(shadowJar)
        var dest = File(rootDir, ".paper/plugins")
        // if plugin.jar exists in plugins change dest to plugins/update
        if (File(dest, shadowJar.get().archiveFileName.get()).exists()) dest = File(dest, "update")
        into(dest)
    }
}

publishing {
    publications {
        create<MavenPublication>("Psychics") {
            artifactId = "psychics"
            from(components["java"])
            artifact(tasks["sourcesJar"])
        }
    }
}