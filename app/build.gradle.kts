plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.trieur.screenshots"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trieur.screenshots"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // L'OCR embarque ~10 Mo de code natif par architecture. Seuls les téléphones ARM 64 bits
        // sont visés (dont le Galaxy S24) : sans ce filtre, x86, x86_64 et armeabi-v7a
        // ajoutent ~29 Mo. À élargir si on teste un jour sur un émulateur (x86_64).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            // R8 retire le code et les ressources inutilisés (Compose, icônes, ML Kit…).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signé avec la clé de debug pour pouvoir l'installer directement.
            // Avant toute publication, il faudra une vraie clé de signature.
            signingConfig = signingConfigs.getByName("debug")
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
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // OCR bundled (modèle embarqué dans l'APK, fonctionne hors ligne)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
