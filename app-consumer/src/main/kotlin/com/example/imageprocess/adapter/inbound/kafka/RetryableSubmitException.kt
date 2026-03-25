package com.example.imageprocess.adapter.inbound.kafka

class RetryableSubmitException(
    message: String,
) : RuntimeException(message)
