import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover)
}

allprojects {
    group = "com.example.imageprocess"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")

        configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(25)
            }
        }

        configure<KotlinJvmProjectExtension> {
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            }
        }

        configure<KtlintExtension> {
            version.set(libs.versions.ktlint)
            android.set(false)
            outputToConsole.set(true)
            reporters {
                reporter(ReporterType.PLAIN)
                reporter(ReporterType.CHECKSTYLE)
            }
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}
