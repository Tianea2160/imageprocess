package com.example.imageprocess

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withVal
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import jakarta.persistence.Entity
import org.hibernate.annotations.DynamicInsert
import org.hibernate.annotations.DynamicUpdate
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
                clazz.hasAnnotationOf(DynamicInsert::class) &&
                    clazz.hasAnnotationOf(DynamicUpdate::class)
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
    fun `domain layer should not use Spring or JPA annotations except Page in ports`() {
        val allowedInPorts = setOf("org.springframework.data.domain.")

        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.contains("/domain/") }
            .assertFalse { file ->
                val isPort = file.path.contains("/domain/port/")
                file.imports.any { import ->
                    val isSpring = import.name.startsWith("org.springframework.")
                    val isJpa = import.name.startsWith("jakarta.persistence.")
                    val isHibernate = import.name.startsWith("org.hibernate.")
                    val isAllowed = isPort && allowedInPorts.any { import.name.startsWith(it) }
                    (isSpring || isJpa || isHibernate) && !isAllowed
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
            .withVal()
            .assertTrue { property ->
                property.annotations.any { annotation ->
                    annotation.name == "Schema" &&
                        annotation.text.contains("requiredMode")
                }
            }
    }

    @Test
    fun `controller should not have swagger annotations`() {
        val swaggerAnnotations =
            setOf("Operation", "Parameter", "ApiResponse", "ApiResponses", "Tag")

        Konsist
            .scopeFromProject()
            .classes()
            .withAnnotationOf(RestController::class)
            .assertFalse { clazz ->
                clazz.annotations.any { it.name in swaggerAnnotations } ||
                    clazz.functions().any { function ->
                        function.annotations.any { it.name in swaggerAnnotations } ||
                            function.parameters.any { param ->
                                param.annotations.any { it.name in swaggerAnnotations }
                            }
                    }
            }
    }

    @Test
    fun `source code should not use require, check, or error for validation`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { !it.path.contains("/test/") }
            .assertFalse { file ->
                file.text.contains(Regex("""(?m)^\s*require\s*[({]""")) ||
                    file.text.contains(Regex("""(?m)^\s*require\s*\(.*\)\s*\{""")) ||
                    file.text.contains(Regex("""(?m)^\s*check\s*[({]""")) ||
                    file.text.contains(Regex("""(?m)^\s*check\s*\(.*\)\s*\{""")) ||
                    file.text.contains(Regex("""(?m)(?<!\.)error\s*\("""))
            }
    }

    @Test
    fun `source code should not use non-null assertion operator`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { !it.path.contains("/test/") }
            .assertFalse { file ->
                file.text.contains(Regex("""(?<!\*)!!"""))
            }
    }

    @Test
    fun `api interface should not have spring web annotations`() {
        val springWebAnnotations =
            setOf(
                "GetMapping",
                "PostMapping",
                "PutMapping",
                "DeleteMapping",
                "PatchMapping",
                "RequestMapping",
                "RequestBody",
                "PathVariable",
                "RequestParam",
                "ResponseStatus",
                "RestController",
            )

        Konsist
            .scopeFromProject()
            .interfaces()
            .withNameEndingWith("Api")
            .filter { it.resideInPath("adapter/inbound/web") }
            .assertFalse { iface ->
                iface.annotations.any { it.name in springWebAnnotations } ||
                    iface.functions().any { function ->
                        function.annotations.any { it.name in springWebAnnotations } ||
                            function.parameters.any { param ->
                                param.annotations.any { it.name in springWebAnnotations }
                            }
                    }
            }
    }
}
