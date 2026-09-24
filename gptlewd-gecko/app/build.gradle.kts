plugins {
    id("com.android.application")
}

android {
    namespace = "com.ximbica.gptlewd.gecko"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.ximbica.gptlewd.gecko"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-alpha1"

        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:156.0.20260921121718")
}
