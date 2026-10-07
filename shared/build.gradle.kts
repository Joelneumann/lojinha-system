plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

abstract class GenerateAppVersionTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputFile = outputDir.get().file("de/joelneumann/lojinha/AppVersion.kt").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package de.joelneumann.lojinha

            object AppVersion {
                const val CURRENT = "${version.get()}"
            }
            """.trimIndent() + "\n"
        )
    }
}

val generateAppVersion by tasks.registering(GenerateAppVersionTask::class) {
    version.set(providers.gradleProperty("app.version").orElse("1.0.0"))
    outputDir.set(layout.buildDirectory.dir("generated/sources/version/kotlin"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "shared"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "lojinha.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppVersion.map { it.outputDir })
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        jvmMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspJvm", libs.androidx.room.compiler)
}