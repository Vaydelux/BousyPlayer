plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.vaydelux.bousyplayer"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vaydelux.bousyplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 28
        versionName = "0.28.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Two installable debug APKs: no 300MB universal native-library bundle.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            // VideoLAN and libmpv both bundle C++ runtime; device tests remain necessary.
            pickFirsts += setOf("**/libc++_shared.so")
        }
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Transitive Guava 33.3.1-android was identified by the new OSV gate.
    // Enforce the patched Android variant for CVE-2026-102554
    // (GHSA-xxph-c9ww-hj94); do not silently suppress the advisory.
    constraints {
        implementation("com.google.guava:guava:33.7.2-android") {
            because("Fix uncontrolled memory allocation on deserialization (GHSA-xxph-c9ww-hj94)")
        }
    }
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("org.videolan.android:libvlc-all:3.6.5")
    implementation("dev.jdtech.mpv:libmpv:1.0.0")
    testImplementation("junit:junit:4.13.2")
    // Instrumentation tests compile in CI; execution still requires a device/emulator.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

/**
 * CycloneDX 1.5 inventory of resolved Maven modules on the Android debug
 * runtime classpath. Unlike parsing build.gradle declarations, this includes
 * transitive dependencies and resolves version conflicts to actual versions.
 *
 * IMPORTANT: native C/C++ libraries embedded inside AARs are not inventoried
 * individually; they require a separate license and vulnerability audit.
 */
tasks.register("generateDebugSbom") {
    group = "verification"
    description = "Generate CycloneDX JSON from the resolved debugRuntimeClasspath"
    doLast {
        val components = configurations.getByName("debugRuntimeClasspath")
            .incoming.resolutionResult.allComponents.mapNotNull { component ->
                val module = component.id as?
                    org.gradle.api.artifacts.component.ModuleComponentIdentifier
                    ?: return@mapNotNull null
                val purl = "pkg:maven/${module.group}/${module.module}@${module.version}"
                mapOf(
                    "type" to "library",
                    "group" to module.group,
                    "name" to module.module,
                    "version" to module.version,
                    "purl" to purl,
                    "bom-ref" to purl
                )
            }.distinctBy { it.getValue("purl") }
            .sortedBy { it.getValue("purl") }
        check(components.isNotEmpty()) { "Refusing to publish an empty dependency inventory" }
        val inventory = mapOf(
            "bomFormat" to "CycloneDX",
            "specVersion" to "1.5",
            "version" to 1,
            "metadata" to mapOf(
                "component" to mapOf(
                    "type" to "application",
                    "name" to "BousyPlayer",
                    "version" to (android.defaultConfig.versionName ?: "unknown")
                ),
                "properties" to listOf(
                    mapOf("name" to "bousyplayer:inventory-scope",
                          "value" to "Resolved Maven debugRuntimeClasspath modules only; not embedded native C/C++ libraries")
                )
            ),
            "components" to components
        )
        val destination = layout.buildDirectory.file("reports/security/debug-maven-sbom.cdx.json")
            .get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(groovy.json.JsonOutput.prettyPrint(
            groovy.json.JsonOutput.toJson(inventory)) + "\n")
        println("CycloneDX Maven inventory: ${components.size} modules at ${destination}")
    }
}
