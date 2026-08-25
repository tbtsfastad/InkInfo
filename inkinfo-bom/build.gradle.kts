plugins {
    id("java-platform")
    id("maven-publish")
}

group = "com.github.tbtsfastad"
version = "1.0.3"

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api("com.github.tbtsfastad.InkInfo:inkinfo:1.0.3")
        api("com.github.tbtsfastad.InkInfo:inkump:1.0.3")
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["javaPlatform"])
            groupId = "com.github.tbtsfastad"
            artifactId = "inkinfo-bom"
            version = "1.0.3"
        }
    }
}
