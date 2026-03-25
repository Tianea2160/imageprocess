package com.example.imageprocess

import com.example.imageprocess.application.TaskPollingService
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
class PollingLifecycleManager(
    private val taskPollingService: TaskPollingService,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var running = false

    override fun start() {
        log.info("Polling lifecycle starting — recovering incomplete tasks")
        taskPollingService.recoverOnStartup()
        taskPollingService.startPolling()
        running = true
        log.info("Polling lifecycle started")
    }

    override fun stop() {
        log.info("Polling lifecycle stopping")
        taskPollingService.stopPolling()
        running = false
        log.info("Polling lifecycle stopped")
    }

    override fun stop(callback: Runnable) {
        stop()
        callback.run()
    }

    override fun isRunning(): Boolean = running

    override fun isAutoStartup(): Boolean = true

    /**
     * Kafka Listener는 phase MAX_VALUE - 100에서 동작.
     * Polling은 그보다 낮은 phase로 설정하여:
     *   - 시작: Polling(복구 + 활성화) → Kafka Listener 시작
     *   - 종료: Kafka Listener 중단 → Polling 중단
     */
    override fun getPhase(): Int = Int.MAX_VALUE - 200
}
