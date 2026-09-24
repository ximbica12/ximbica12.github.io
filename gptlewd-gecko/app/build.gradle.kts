plugins {
    id("com.android.application")
}

android {
    namespace = "com.ximbica.gptlewd.gecko"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        // Alpha 2 uses a parallel package so the working Alpha 1 stays installed.
        applicationId = "com.ximbica.gptlewd.gecko.a2"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0-alpha2-j7"

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
            // Required for reliable native-library extraction on old Samsung/Android 8 installers.
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
