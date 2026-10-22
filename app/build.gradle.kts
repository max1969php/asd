plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.asd.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.asd.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"

    }

    // Python e yt-dlp (TUBO) sono librerie native da ~15 MB per CPU: un APK per CPU, più uno universale.
    // Quello universale è il download principale: alcuni telefoni con CPU a 64 bit hanno Android a
    // 32 bit (es. Galaxy A13 SM-A137F) e rifiutano l'APK arm64 come "non compatibile".
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    // youtubedl-android legge Python dai file .so estratti su disco: niente librerie compresse nell'APK.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            // Firmato con la chiave debug: stesso SHA-1 già registrato su Google Cloud, si installa
            // sopra la versione debug. Per distribuire davvero serve una chiave di release propria.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// x86 serve solo agli emulatori del PC: fuori dalla release, così l'APK universale ha solo ARM.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.jniLibs.excludes.addAll("lib/x86/**", "lib/x86_64/**")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.play.services.auth)
    implementation(libs.youtubedl.android) {
        // La libreria dichiara appcompat ma non lo usa: sarebbero ~1 MB di risorse per niente.
        exclude(group = "androidx.appcompat")
    }

    testImplementation(libs.junit)
    // org.json reale nei test JVM (quello di android.jar è uno stub).
    testImplementation(libs.org.json)
}
