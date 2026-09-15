import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(compose.ui)
    implementation(compose.foundation)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    implementation("org.xerial:sqlite-jdbc:3.45.2.0")
    implementation("org.openjfx:javafx-media:21.0.2:win")
    implementation("org.openjfx:javafx-base:21.0.2:win")
    implementation("org.openjfx:javafx-graphics:21.0.2:win")
    implementation(libs.newpipe.extractor)
}

compose.desktop {
    application {
        mainClass = "com.sonara.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "SonaraStream"
            packageVersion = "4.0.0"
            description = "Sonara Stream - 100% Native Lossless Music Player"
            copyright = "Copyright (C) 2026 Sonara Stream. Licensed under GPL-3.0"
            vendor = "Sonara"
            modules("java.sql", "java.naming", "java.instrument", "jdk.unsupported", "jdk.httpserver")

            windows {
                menuGroup = "Sonara"
                upgradeUuid = "d7c8b9a0-1234-5678-9abc-def012345678"
            }
        }
    }
}
