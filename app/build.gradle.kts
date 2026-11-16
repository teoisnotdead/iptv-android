import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Datos por instalación (panel y marca). Viven en local.properties, fuera de git.
val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String, default: String = ""): String = (local.getProperty(key) ?: default).trim()
fun quoted(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// El decodificador FFmpeg debe coincidir con la versión de Media3.
val media3 = "1.8.0"

android {
    namespace = "app.iptv.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.iptv.player"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        // panel.url: servidor que aparece por defecto en el login.
        // panel.lock=true: versión de revendedor; el servidor queda fijo y el campo no se muestra.
        buildConfigField("String", "PANEL_URL", quoted(cfg("panel.url").trimEnd('/')))
        buildConfigField("boolean", "PANEL_LOCKED", (cfg("panel.lock") == "true" && cfg("panel.url").isNotEmpty()).toString())
        buildConfigField("String", "SITE_NAME", quoted(cfg("brand.name", "Lytra TV")))
        buildConfigField("String", "BRAND_COLOR", quoted(cfg("brand.color", "#a78bfa")))
        resValue("string", "app_name", cfg("brand.name", "Lytra TV"))
        // El ícono de la app usa el color de marca de la instalación.
        resValue("color", "brand", cfg("brand.color", "#a78bfa").let { if (Regex("^#[0-9a-fA-F]{6}$").matches(it)) it else "#a78bfa" })
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    // Audio AC-3 / E-AC-3 / DTS / TrueHD por software cuando el teléfono no trae decodificador.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:$media3+1")

    testImplementation("junit:junit:4.13.2")
}
