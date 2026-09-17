import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    // Ktor Server dependencies
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Room Runtime for AppDatabase reference
    implementation(libs.androidx.room.runtime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

val copyWasmResources by tasks.registering(Copy::class) {
    description = "Copies production Wasm browser distribution into desktopApp resources under wasm/"
    dependsOn(":shared:wasmJsBrowserDistribution")
    from(project(":shared").layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(layout.buildDirectory.dir("generated/wasmResources/wasm"))
}

sourceSets {
    named("main") {
        resources.srcDir(copyWasmResources.map { it.destinationDir.parentFile })
    }
}

tasks.named("processResources") {
    dependsOn(copyWasmResources)
}

compose.desktop {
    application {
        mainClass = "de.joelneumann.lojinha.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Lojinha System"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "de.joelneumann.lojinha"
                iconFile.set(project.file("src/main/resources/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
            linux {
                packageName = "lojinha-system"
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}