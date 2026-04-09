import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
}

group = "dev.marcal.mediapulse.server"
version = "1.0.0-beta.32"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

springBoot {
    mainClass = "dev.marcal.mediapulse.server.Boot"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("commons-codec:commons-codec:1.19.0")

    implementation("org.xerial:sqlite-jdbc:3.50.2.0")

    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("io.mockk:mockk:1.14.5")
}

tasks.test {
    useJUnitPlatform {
        if (project.hasProperty("skipIntegrationTests")) {
            excludeTags("integration")
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.jar {
    enabled = false
}

abstract class FingerprintFrontendTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fingerprint() {
        val sourceDir = inputDir.get().asFile
        val destinationDir = outputDir.get().asFile

        project.delete(destinationDir)
        destinationDir.mkdirs()

        val assetMappings =
            sourceDir
                .walkTopDown()
                .filter(File::isFile)
                .map { it.relativeTo(sourceDir).invariantSeparatorsPath }
                .filterNot { it == "README.md" || it.endsWith(".html") }
                .associateWith { relativePath ->
                    fingerprintedPath(relativePath, File(sourceDir, relativePath))
                }

        assetMappings.forEach { (sourceRelativePath, targetRelativePath) ->
            val sourceFile = File(sourceDir, sourceRelativePath)
            val targetFile = File(destinationDir, targetRelativePath)
            targetFile.parentFile.mkdirs()
            sourceFile.copyTo(targetFile, overwrite = true)
        }

        sourceDir
            .walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(sourceDir).invariantSeparatorsPath }
            .filter { it.endsWith(".html") }
            .forEach { relativePath ->
                val htmlFile = File(sourceDir, relativePath)
                val rewrittenHtml =
                    assetMappings.entries.fold(htmlFile.readText()) { content, (sourceRelativePath, targetRelativePath) ->
                        content.replace("./$sourceRelativePath", "./$targetRelativePath")
                    }

                val targetFile = File(destinationDir, relativePath)
                targetFile.parentFile.mkdirs()
                targetFile.writeText(rewrittenHtml)
            }
    }

    private fun fingerprintedPath(
        relativePath: String,
        file: File,
    ): String {
        val extensionIndex = relativePath.lastIndexOf('.')
        val fingerprint = sha256(file.readBytes()).take(10)
        return if (extensionIndex < 0) {
            "$relativePath-$fingerprint"
        } else {
            "${relativePath.substring(0, extensionIndex)}-$fingerprint${relativePath.substring(extensionIndex)}"
        }
    }

    private fun sha256(content: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(content)
            .joinToString("") { byte -> "%02x".format(byte) }
}

val fingerprintFrontendStatic by tasks.registering(FingerprintFrontendTask::class) {
    inputDir.set(project.layout.projectDirectory.dir("../frontend"))
    outputDir.set(layout.buildDirectory.dir("generated/frontend-static"))
}

tasks.processResources {
    dependsOn(fingerprintFrontendStatic)
    from(fingerprintFrontendStatic) {
        into("static")
    }
}
