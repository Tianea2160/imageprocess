package com.example.imageprocess.adapter.inbound.web

import com.example.imageprocess.domain.exception.BadRequestException
import com.example.imageprocess.domain.exception.BusinessException
import com.example.imageprocess.domain.exception.ConflictException
import com.example.imageprocess.domain.exception.ForbiddenException
import com.example.imageprocess.domain.exception.NotFoundException
import com.example.imageprocess.domain.exception.UnauthorizedException
import com.example.imageprocess.domain.exception.UnprocessableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(e: BadRequestException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.from(e))

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(e: UnauthorizedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.from(e))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(e: ForbiddenException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.from(e))

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.from(e))

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(e: ConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.from(e))

    @ExceptionHandler(UnprocessableException::class)
    fun handleUnprocessable(e: UnprocessableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.from(e))

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<ErrorResponse> {
        log.error("Unhandled business exception", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.from(e))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("Internal server error", "INTERNAL_SERVER_ERROR", Instant.now()))
    }
}

@io.swagger.v3.oas.annotations.media.Schema(description = "에러 응답")
data class ErrorResponse(
    @field:io.swagger.v3.oas.annotations.media.Schema(
        description = "에러 메시지",
        requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
    )
    val message: String,
    @field:io.swagger.v3.oas.annotations.media.Schema(
        description = "에러 코드",
        example = "TASK_NOT_FOUND",
        requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
    )
    val code: String,
    @field:io.swagger.v3.oas.annotations.media.Schema(
        description = "에러 발생 시각",
        requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
    )
    val timestamp: Instant,
) {
    companion object {
        fun from(e: BusinessException): ErrorResponse = ErrorResponse(e.message, e.code, Instant.now())
    }
}
