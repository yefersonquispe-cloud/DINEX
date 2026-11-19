import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val dinexLocalProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
}
// Solo durante la prueba (GEMINI_KEY_IN_APK=true en local.properties): la clave de Gemini va en el APK
// como respaldo si Firebase no puede verificar la app. Con false (o sin la línea) el APK no lleva clave.
val geminiKeyInApk = dinexLocalProperties.getProperty("GEMINI_KEY_IN_APK", "false").trim().equals("true", ignoreCase = true)
val geminiTestKey = if (geminiKeyInApk) dinexLocalProperties.getProperty("GEMINI_API_KEY", "").trim() else ""

// Firebase AI Logic: la configuración de Android sale de google-services.json (no se sube a Git).
// La clave de Gemini ya no se incluye en el APK; Firebase la guarda en su servidor.
fun firebaseAndroidConfig(packageName: String): Map<String, String> {
    val file = rootProject.file("androidApp/google-services.json")
    if (!file.exists()) return emptyMap()
    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper().parse(file) as Map<String, Any?>
    val project = json["project_info"] as? Map<String, Any?> ?: return emptyMap()
    val client = (json["client"] as? List<Map<String, Any?>>).orEmpty().firstOrNull {
        ((it["client_info"] as? Map<*, *>)?.get("android_client_info") as? Map<*, *>)?.get("package_name") == packageName
    } ?: return emptyMap()
    val appId = (client["client_info"] as? Map<*, *>)?.get("mobilesdk_app_id")?.toString().orEmpty()
    val apiKey = ((client["api_key"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key")?.toString().orEmpty()
    if (appId.isBlank() || apiKey.isBlank()) return emptyMap()
    return mapOf(
        "google_app_id" to appId,
        "google_api_key" to apiKey,
        "project_id" to project["project_id"].toString(),
        "gcm_defaultSenderId" to project["project_number"].toString(),
        "google_storage_bucket" to project["storage_bucket"]?.toString().orEmpty(),
    )
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.firebase.bom))
    // App Check: Play Integrity en la versión publicada y un token de prueba en las versiones debug.
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.example.dinex"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.yefersonquispe.dinex"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 7
        versionName = "1.6"
        manifestPlaceholders["geminiApiKey"] = geminiTestKey
        firebaseAndroidConfig(applicationId.orEmpty()).forEach { (name, value) -> resValue("string", name, value) }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Generate device-specific APKs so Android 11 phones do not need to
    // download native ML Kit binaries for unrelated CPU architectures.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
    buildTypes {
        release {
            // R8 quita el código sin uso y achica la app para que cargue más rápido.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}
