import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Partagé entre l'interface (app.toque) et le pilote moteur (app.toque.pilote) : messages
// échangés entre les deux, journal, réglages de pilotage.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.toque.commun"
    compileSdk = 34
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets["main"].java.srcDirs("src/main/kotlin")
    lint { abortOnError = false }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    api(project(":core"))
}
