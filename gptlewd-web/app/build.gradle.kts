plugins {
    id("com.android.application")
}

android {
    namespace = "com.ximbica.gptlewd.web"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ximbica.gptlewd.web"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-alpha1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
