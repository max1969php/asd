import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

/** Versione di ASD Desktop: finisce nel .deb e nella schermata SETUP. */
val desktopVersion = "0.1.0"

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Tema e componenti dell'app Android (solo Compose, niente Android): compilati così come sono.
val androidUi = "../app/src/main/java/com/asd/player/ui"
val generatedVersion = layout.buildDirectory.dir("generated/version")
sourceSets.main {
    kotlin.srcDir("$androidUi/theme")
    kotlin.srcDir("$androidUi/components")
    kotlin.srcDir(generatedVersion)
    kotlin.exclude("**/AsdLinks.kt") // usa Intent e L10n di Android
}

val writeVersion by tasks.registering {
    val out = generatedVersion
    val version = desktopVersion
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("com/asd/desktop/Version.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package com.asd.desktop\n\nobject Version {\n    const val NAME = \"$version\"\n}\n")
    }
}
tasks.named("compileKotlin") { dependsOn(writeVersion) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.gst1.java.core)
    implementation(libs.jaudiotagger)
    implementation(libs.org.json)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.asd.desktop.MainKt"
        nativeDistributions {
            // Il .deb lo fa packaging/make-deb.sh (dipendenze GStreamer, niente fakeroot): qui serve l'immagine dell'app.
            targetFormats(TargetFormat.Deb)
            packageName = "asd-desktop"
            packageVersion = desktopVersion
            description = "ASD Desktop"
            modules("java.instrument", "java.logging", "jdk.unsupported")
        }
        // Niente ProGuard: GStreamer-Java e JNA vivono di riflessione (come yt-dlp con R8 su Android).
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}

/** ./gradlew :desktop:packageAsdDeb → desktop/build/deb/asd-desktop_<versione>_amd64.deb */
val packageAsdDeb by tasks.registering(Exec::class) {
    group = "distribution"
    dependsOn("createReleaseDistributable")
    val app = layout.buildDirectory.dir("compose/binaries/main-release/app/asd-desktop")
    val out = layout.buildDirectory.file("deb/asd-desktop_${desktopVersion}_amd64.deb")
    commandLine(
        "bash", "packaging/make-deb.sh",
        app.get().asFile.absolutePath, desktopVersion,
        file("src/main/resources/asd.png").absolutePath, out.get().asFile.absolutePath,
    )
}
