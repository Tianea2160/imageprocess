plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.test.junit5)

    // Annotations referenced in convention checks
    testImplementation(libs.spring.boot.starter.webmvc)
    testImplementation(libs.springdoc.openapi.starter.webmvc.scalar)
    testImplementation("jakarta.persistence:jakarta.persistence-api")
    testImplementation("org.hibernate.orm:hibernate-core")
    testRuntimeOnly(libs.junit.platform.launcher)
}
