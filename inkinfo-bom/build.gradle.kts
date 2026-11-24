plugins {
    id("java-platform")
    id("maven-publish")
}

group = "com.github.tbtsfastad"
version = libs.versions.inkinfoRelease.get()

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api("com.github.tbtsfastad.InkInfo:inkinfo:${project.version}")
        api("com.github.tbtsfastad.InkInfo:inkump:${project.version}")
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["javaPlatform"])
            groupId = "com.github.tbtsfastad"
            artifactId = "inkinfo-bom"
            version = libs.versions.inkinfoRelease.get()
        }
    }
}
