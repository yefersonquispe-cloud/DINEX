import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Clave de Gemini solo para la web (GEMINI_WEB_API_KEY en local.properties). Se genera en build/,
// nunca se guarda en el repositorio. Sin clave, Dinex IA en la web usa respuestas locales.
val webGeminiKey = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orElse("")
    .map { text -> text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("GEMINI_WEB_API_KEY=") }?.substringAfter('=')?.trim().orEmpty() }
    .orElse(providers.environmentVariable("GEMINI_WEB_API_KEY"))
val webSecretsDir = layout.buildDirectory.dir("generated/webSecrets/kotlin")
val generateWebSecrets = tasks.register("generateWebSecrets") {
    val key = webGeminiKey
    val output = webSecretsDir
    inputs.property("geminiWebKey", key)
    outputs.dir(output)
    doLast {
        val dir = output.get().asFile.resolve("com/example/dinex").apply { mkdirs() }
        val safe = key.get().filter { it.isLetterOrDigit() || it in "._-" }
        dir.resolve("WebSecrets.kt").writeText(
            "package com.example.dinex\n\n/** Generado desde local.properties. No editar. */\ninternal const val WEB_GEMINI_API_KEY = \"$safe\"\n",
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        iosTarget.compilations.getByName("main") {
            cinterops.create("sqlite3") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/sqlite3.def"))
            }
        }
    }
    
    js {
        browser()
        nodejs()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
    }
    
    android {
       namespace = "com.example.dinex.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation("androidx.credentials:credentials:1.3.0")
            implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
            implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
            implementation("com.google.mlkit:text-recognition:16.0.1")
            implementation("com.google.mlkit:image-labeling:17.0.9")
            implementation("com.google.mlkit:barcode-scanning:17.3.0")
            implementation(libs.mlkit.genai.prompt)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
        jsMain { kotlin.srcDir(generateWebSecrets) }
        wasmJsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
        wasmJsMain { kotlin.srcDir(generateWebSecrets) }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
