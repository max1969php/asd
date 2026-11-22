import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Logica di ASD senza Android: dominio (nomi, cartelle, ricerca, doppioni), misura del volume,
// link del TUBO. La usano l'app Android e ASD Desktop.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Su Android org.json c'è già nel sistema: qui solo per compilare, il desktop lo aggiunge da sé.
    compileOnly(libs.org.json)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
