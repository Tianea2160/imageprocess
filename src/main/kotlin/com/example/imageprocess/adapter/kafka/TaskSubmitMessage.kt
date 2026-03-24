package com.example.imageprocess.adapter.kafka

data class TaskSubmitMessage(
    val taskId: String,
    val imageUrl: String,
)
