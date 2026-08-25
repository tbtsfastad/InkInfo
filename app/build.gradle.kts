plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.inkinfo.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.inkinfo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":inkinfo"))
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
