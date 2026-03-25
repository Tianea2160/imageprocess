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

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.data.commons)
    implementation(libs.springdoc.openapi.starter.webmvc.scalar)

    testImplementation(testFixtures(project(":core:domain")))
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}
