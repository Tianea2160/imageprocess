plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.20"
    kotlin("kapt") version "2.3.20"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    `java-test-fixtures`
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "imageprocess"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.github.openfeign.querydsl:querydsl-jpa:7.0")
    implementation("io.github.openfeign.querydsl:querydsl-kotlin:7.0")
    kapt("io.github.openfeign.querydsl:querydsl-apt:7.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-scalar:3.0.2")
    implementation("io.hypersistence:hypersistence-tsid:2.1.4")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.redis:testcontainers-redis:2.2.4")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testFixturesImplementation("com.navercorp.fixturemonkey:fixture-monkey-starter-kotlin:1.1.11")
    testImplementation("io.mockk:mockk:1.14.3")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
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

kapt {
    correctErrorTypes = true
}

afterEvaluate {
    tasks.findByName("kaptTestKotlin")?.enabled = false
    tasks.findByName("kaptGenerateStubsTestKotlin")?.enabled = false
    tasks.findByName("kaptTestFixturesKotlin")?.enabled = false
    tasks.findByName("kaptGenerateStubsTestFixturesKotlin")?.enabled = false
}

ktlint {
    version.set("1.6.0")
    android.set(false)
    outputToConsole.set(true)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}
