package com.example.imageprocess.domain.exception

open class BusinessException(
    override val message: String,
    val code: String,
) : RuntimeException(message)

open class BadRequestException(
    message: String,
    code: String,
) : BusinessException(message, code)

open class UnauthorizedException(
    message: String,
    code: String,
) : BusinessException(message, code)

open class ForbiddenException(
    message: String,
    code: String,
) : BusinessException(message, code)

open class NotFoundException(
    message: String,
    code: String,
) : BusinessException(message, code)

open class ConflictException(
    message: String,
    code: String,
) : BusinessException(message, code)

open class UnprocessableException(
    message: String,
    code: String,
) : BusinessException(message, code)
