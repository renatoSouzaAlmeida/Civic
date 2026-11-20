plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val codigoVersao = (project.findProperty("versionCodeCI") as String?)?.toIntOrNull() ?: 2

android {
    namespace = "br.com.civic.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.civic.launcher"
        // Multimídia TS18 reporta Android 13, mas o build é QP1A (Android 10).
        minSdk = 26
        targetSdk = 34
        versionCode = codigoVersao
        // O nome (MAIOR.MENOR.PATCH, nenhuma parte acima de 99) é calculado no CI a partir de versao.properties.
        versionName = (project.findProperty("versionNameCI") as String?) ?: "0.0.$codigoVersao"
    }

    // Chave de assinatura fixa (obrigatória para a atualização automática funcionar):
    // vem de variáveis de ambiente preenchidas pelos segredos do GitHub, nunca do repositório.
    val arquivoChave = System.getenv("CIVIC_KEYSTORE")
    if (arquivoChave != null) {
        signingConfigs.create("civic") {
            storeFile = file(arquivoChave)
            storePassword = System.getenv("CIVIC_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("CIVIC_KEY_ALIAS")
            keyPassword = System.getenv("CIVIC_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // App pequeno: R8 + remoção de recursos não usados.
            signingConfigs.findByName("civic")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
}
