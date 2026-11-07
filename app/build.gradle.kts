plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.filiz"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.example.filiz"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // online: küçük APK, ek sesler uygulama içinden indirilir (Play Store için)
    // offline: tüm sesler APK'nın içinde, internet gerekmez (elden dağıtım için)
    flavorDimensions += "dagitim"
    productFlavors {
        create("online") {
            dimension = "dagitim"
        }
        create("offline") {
            dimension = "dagitim"
            versionNameSuffix = "-offline"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val roomVersion = "2.6.1"
    val kotlinVersion = "2.0.20"

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.poi:poi-scratchpad:3.17")
    implementation("com.rmtheis:tess-two:9.1.0")
}
