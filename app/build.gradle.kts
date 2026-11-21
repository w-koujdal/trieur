import java.util.Properties

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
        versionCode = 3
        versionName = "1.2"

        // L'OCR embarque ~10 Mo de code natif par architecture. Seuls les téléphones ARM 64 bits
        // sont visés (dont le Galaxy S24) : sans ce filtre, x86, x86_64 et armeabi-v7a
        // ajoutent ~29 Mo. À élargir si on teste un jour sur un émulateur (x86_64).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Clé de signature de publication : chemin et mots de passe dans keystore.properties
    // (à la racine, hors git). Sans ce fichier, la release est signée avec la clé de debug.
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.exists()) {
        val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        signingConfigs.getByName("debug")
    }

    buildTypes {
        debug {
            // Debug seulement : x86_64 en plus pour tester sur l'émulateur du PC.
            ndk {
                abiFilters += "x86_64"
            }
        }
        release {
            // R8 retire le code et les ressources inutilisés (Compose, icônes, ML Kit…).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
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
    packaging {
        // Code natif ML Kit (~21 Mo) compressé dans l'APK : fichier à télécharger ~2× plus léger
        // pour une appli distribuée hors Play Store. Android le décompresse à l'installation.
        jniLibs {
            useLegacyPackaging = true
        }
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
    // Étiquetage d'images bundled (hors ligne, gratuit) : trier les photos par contenu
    implementation(libs.mlkit.image.labeling)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
