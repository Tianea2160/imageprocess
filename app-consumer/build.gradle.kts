plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    implementation(project(":infra:persistence"))
    implementation(project(":infra:kafka"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:mockworker"))

    implementation(libs.spring.boot.starter.kafka)

    testImplementation(testFixtures(project(":core:domain")))
    testImplementation(libs.bundles.testing)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.spring.boot.starter.kafka.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
