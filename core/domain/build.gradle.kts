plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

dependencies {
    compileOnly(libs.spring.data.commons)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    testFixturesImplementation(libs.fixturemonkey.starter.kotlin)
}
