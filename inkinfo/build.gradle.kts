plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.example.inkinfo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.google.play.services.ads.identifier)
    implementation(libs.google.play.install.referrer)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.tbtsfastad.InkInfo"
            artifactId = "inkinfo"
            version = "1.0.1"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            name = "projectLocal"
            url = uri(rootProject.layout.projectDirectory.dir("maven-repo"))
        }
    }
}
