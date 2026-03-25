package com.example.imageprocess.domain.port.outbound

interface TaskEventPublisher {
    fun publishSubmitTask(
        taskId: String,
        imageUrl: String,
    )
}
