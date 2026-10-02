import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.zxing.core)
    implementation(libs.jna.platform)
}

val appVersion = "2.0.1"

compose.desktop {
    application {
        mainClass = "com.sharex.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m", "-Dsun.java2d.uiScale.enabled=true")

        // jpackage is needed for installers. Point this at any full JDK 17+ if the one running Gradle lacks it:
        //   ./gradlew :desktop:packageMsi -Psharex.packagingJdk="C:/Program Files/Microsoft/jdk-21"
        (providers.gradleProperty("sharex.packagingJdk").orNull ?: System.getenv("SHAREX_PACKAGING_JDK"))?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            // Keep the native executable name stable; the displayed product name is ShareX.
            packageName = "ShareX"
            packageVersion = appVersion
            // Shown by Windows in Task Manager and the firewall prompt.
            description = "ShareX"
            vendor = "ShareX"
            copyright = "ShareX"
            modules("java.naming", "jdk.crypto.ec", "java.management", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icons/sharex.ico"))
                menuGroup = "ShareX"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8f5b7c1e-3d2a-4b8e-9a61-5c0f2e7d4a19"
            }
            macOS {
                bundleID = "com.sharex.desktop"
            }
            linux {
                iconFile.set(project.file("icons/sharex.png"))
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

// Branded single-file installer: ./gradlew :desktop:packageSetup -Psharex.packagingJdk=<JDK with jpackage>
tasks.register<Exec>("packageSetup") {
    group = "compose desktop"
    description = "Builds desktop/build/setup/ShareXSetup-<version>.exe, the ShareX per-user installer."
    dependsOn("createReleaseDistributable")
    val appImage = layout.buildDirectory.dir("compose/binaries/main-release/app/ShareX").get().asFile
    val script = rootProject.file("installer/build.ps1")
    inputs.dir(rootProject.file("installer"))
    inputs.dir(appImage)
    outputs.dir(layout.buildDirectory.dir("setup"))
    commandLine(
        "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath,
        "-AppImage", appImage.absolutePath, "-Version", appVersion,
    )
}
