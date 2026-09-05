plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.ecole2recte.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.ecole2recte.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Une erreur de lint fait échouer la CI : c'est le seul moyen qu'elle serve.
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        // Application non traduite et assumée comme telle : elle parle français.
        disable += setOf("MissingTranslation", "UnusedResources", "VectorPath")
    }

    signingConfigs {
        create("release") {
            // La clé de signature n'est jamais dans le dépôt. Sans elle, le build release
            // reste possible pour vérifier qu'il compile, signé par la clé de débogage.
            val store = System.getenv("RELEASE_STORE_FILE")
            if (!store.isNullOrBlank() && file(store).exists()) {
                storeFile = file(store)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            // R8 reste désactivé tant que le résultat ne peut pas être essayé sur un
            // appareil : un APK réduit qui plante au lancement serait pire que gros.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Synchronisation de fond : c'est elle qui empêche les widgets d'afficher un cache figé.
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // Verrouillage par empreinte ou code de l'appareil (apporte aussi fragment, dont
    // `BiometricPrompt` a besoin : l'activité principale est donc une FragmentActivity).
    implementation("androidx.biometric:biometric:1.1.0")

    // Le cœur métier (`core`) n'utilise que la bibliothèque standard, `org.json` et les
    // coroutines : il tourne donc tel quel sur la JVM. Ces deux dépendances de test lui
    // donnent une vraie implémentation d'`org.json` — celle d'`android.jar` n'est qu'un
    // squelette — et de quoi exécuter des fonctions suspendues.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
