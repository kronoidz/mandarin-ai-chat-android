import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.mandarin.aichat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mandarin.aichat"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val apiUrl = localProperties.getProperty("OPENAI_API_URL", "https://api.openai.com/v1")
        val apiKey = localProperties.getProperty("OPENAI_API_KEY", "YourApiKey")
        val model = localProperties.getProperty("OPENAI_MODEL", "gpt-4o-mini")

        buildConfigField("String", "OPENAI_API_URL", "\"${apiUrl}\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"${apiKey}\"")
        buildConfigField("String", "OPENAI_MODEL", "\"${model}\"")

        // Google Cloud TTS service account credentials.
        // Set GOOGLE_TTS_CREDENTIALS_PATH in local.properties to point to your
        // service-account JSON key file. The content is embedded at build time.
        val ttsCredentialsPath = localProperties.getProperty("GOOGLE_TTS_CREDENTIALS_PATH", "")
        val ttsCredentialsJson = if (ttsCredentialsPath.isNotEmpty()) {
            file(ttsCredentialsPath).readText(Charsets.UTF_8)
        } else {
            ""
        }
        buildConfigField(
            "String",
            "GOOGLE_TTS_CREDENTIALS",
            "\"${ttsCredentialsJson.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.okhttp)
}
