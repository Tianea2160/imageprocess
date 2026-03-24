package com.example.imageprocess.application

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class StartupRecoveryListener(
    private val taskPollingService: TaskPollingService,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        taskPollingService.recoverOnStartup()
    }
}
