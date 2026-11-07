import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

                // Sesli okuma: sherpa-onnx Java API (yerel kütüphaneler pakete gömülür, bkz. tools/prepare_app_resources.py)
                implementation(files("libs/sherpa-onnx-jvm-1.13.8.jar"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

                // PDF
                implementation("org.apache.pdfbox:pdfbox:3.0.1")
                implementation("org.apache.pdfbox:fontbox:3.0.1")

                // SQLite
                implementation("org.xerial:sqlite-jdbc:3.46.0.0")

                // DOCX
                implementation("org.apache.poi:poi:5.2.5")
                implementation("org.apache.poi:poi-scratchpad:5.2.5")
                implementation("org.apache.poi:poi-ooxml:5.2.5")
                implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
                implementation("commons-collections:commons-collections:3.2.2")

                // HTML
                implementation("org.jsoup:jsoup:1.17.2")

                // Logging
                implementation("org.slf4j:slf4j-simple:2.0.11")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.filiz.desktop.MainKt"
        val windowsIcon = project.file("src/desktopMain/resources/icons/bideng.ico")
        val macIcon = project.file("src/desktopMain/resources/icons/bideng.icns")
        val linuxIcon = project.file("src/desktopMain/resources/icons/bideng.png")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)

            packageName = "Bideng"
            packageVersion = (findProperty("bidengVersion") as String?) ?: "1.1.0"

            description = "Bideng - Offline E-Reader with TTS"
            copyright = "Copyright 2026"
            vendor = "Bideng"

            // Sesler ve sherpa-onnx paket içine gömülür (tools/prepare_app_resources.py hazırlar);
            // kurulan uygulama tamamen çevrimdışı çalışır.
            appResourcesRootDir.set(project.layout.buildDirectory.dir("app-resources"))

            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.sql",
                "java.xml",
                "jdk.charsets",
                "jdk.crypto.ec",
                "jdk.unsupported"
            )

            jvmArgs(
                "-Dfile.encoding=UTF-8",
                "-Dsun.stdout.encoding=UTF-8",
                "-Dsun.stderr.encoding=UTF-8",
                "-Xmx1536m"
            )

            windows {
                if (windowsIcon.exists()) {
                    iconFile.set(windowsIcon)
                }
                menuGroup = "Bideng"
                shortcut = true
                dirChooser = true
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }

            macOS {
                bundleID = "com.bideng.desktop"
                appCategory = "public.app-category.books"
                if (macIcon.exists()) {
                    iconFile.set(macIcon)
                }
            }

            linux {
                if (linuxIcon.exists()) {
                    iconFile.set(linuxIcon)
                }
                shortcut = true
                menuGroup = "Office"
                debMaintainer = "bideng@users.noreply.github.com"
                appCategory = "Office"
            }
        }
    }
}

tasks.matching { it.name == "packageMsi" || it.name == "packageExe" }.configureEach {
    dependsOn("createRuntimeImage")
}

