package com.example.imageprocess

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import jakarta.persistence.Entity
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.RestController

class ArchitectureConventionTest {
    @Test
    fun `controller should not use ResponseEntity as return type`() {
        Konsist
            .scopeFromProject()
            .classes()
            .withAnnotationOf(RestController::class)
            .flatMap { it.functions() }
            .filter { it.hasPublicOrDefaultModifier }
            .assertFalse { function ->
                function.hasReturnType { it.name == "ResponseEntity" || it.text.contains("ResponseEntity") }
            }
    }

    @Test
    fun `JPA entities should use DynamicInsert and DynamicUpdate annotations`() {
        Konsist
            .scopeFromProject()
            .classes()
            .withAnnotationOf(Entity::class)
            .assertTrue { clazz ->
                clazz.hasAnnotationOf(org.hibernate.annotations.DynamicInsert::class) &&
                    clazz.hasAnnotationOf(org.hibernate.annotations.DynamicUpdate::class)
            }
    }

    @Test
    fun `domain layer should not depend on application or adapter`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/domain/") }
            .assertFalse { file ->
                file.imports.any {
                    it.name.contains(".application.") || it.name.contains(".adapter.")
                }
            }
    }

    @Test
    fun `application layer should not depend on adapter`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/application/") }
            .assertFalse { file ->
                file.imports.any { it.name.contains(".adapter.") }
            }
    }

    @Test
    fun `domain layer should not use Spring or JPA annotations`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/domain/") }
            .assertFalse { file ->
                file.imports.any {
                    it.name.startsWith("org.springframework.") ||
                        it.name.startsWith("jakarta.persistence.") ||
                        it.name.startsWith("org.hibernate.")
                }
            }
    }

    @Test
    fun `request and response classes should have Schema annotation`() {
        Konsist
            .scopeFromProject()
            .classes()
            .withNameEndingWith("Request", "Response")
            .filter { it.resideInPath("adapter/inbound/web") }
            .assertTrue { clazz ->
                clazz.hasAnnotationOf(io.swagger.v3.oas.annotations.media.Schema::class)
            }
    }

    @Test
    fun `request and response properties should have Schema annotation with requiredMode`() {
        Konsist
            .scopeFromProject()
            .classes()
            .withNameEndingWith("Request", "Response")
            .filter { it.resideInPath("adapter/inbound/web") }
            .flatMap { it.properties() }
            .filter { !it.hasVarModifier }
            .assertTrue { property ->
                property.annotations.any { annotation ->
                    annotation.name == "Schema" &&
                        annotation.text.contains("requiredMode")
                }
            }
    }
}
