package com.example.imageprocess.adapter.inbound.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "이미지 처리 작업 생성 요청")
data class CreateTaskRequest(
    @Schema(
        description = "처리할 이미지의 URL",
        example = "https://example.com/image.png",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val imageUrl: String,
)
