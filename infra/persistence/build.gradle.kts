plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.querydsl.jpa)
    implementation(libs.querydsl.kotlin)
    kapt(libs.querydsl.apt)
    implementation(libs.hypersistence.tsid)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

kapt {
    correctErrorTypes = true
}

afterEvaluate {
    tasks.findByName("kaptTestKotlin")?.enabled = false
    tasks.findByName("kaptGenerateStubsTestKotlin")?.enabled = false
}
