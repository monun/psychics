tasks {
    shadowJar {
        archiveBaseName.set("Psychics")
    }
    create<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }
    create<Copy>("copyToServer") {
        from(shadowJar)
        var dest = File(rootDir, ".server/plugins")
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