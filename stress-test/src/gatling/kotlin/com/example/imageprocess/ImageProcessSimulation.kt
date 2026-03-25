package com.example.imageprocess

import io.gatling.javaapi.core.CoreDsl.StringBody
import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.details
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.CoreDsl.nothingFor
import io.gatling.javaapi.core.CoreDsl.rampUsersPerSec
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.time.Duration
import java.util.UUID

class ImageProcessSimulation : Simulation() {
    private val baseUrl = System.getProperty("baseUrl", "http://localhost:18082")

    private val httpProtocol =
        http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")

    /**
     * Task 생성 요청.
     * 중복 방지(fingerprint)를 위해 매 요청마다 고유 UUID를 imageUrl에 포함한다.
     */
    private val submitTask =
        io.gatling.javaapi.core.CoreDsl
            .exec { session ->
                session.set("imageUrl", "https://example.com/stress-test/${UUID.randomUUID()}.png")
            }.exec(
                http("Create Task")
                    .post("/api/tasks")
                    .body(StringBody("""{"imageUrl": "#{imageUrl}"}"""))
                    .asJson()
                    .check(
                        status().shouldBe(202),
                        io.gatling.javaapi.core.CoreDsl
                            .jsonPath("$.taskId")
                            .saveAs("taskId"),
                    ),
            )

    /**
     * 상태 폴링: COMPLETED 또는 FAILED가 될 때까지 반복.
     * 최대 60회(약 60초) 시도 후 실패 처리.
     */
    private val pollUntilDone =
        io.gatling.javaapi.core.CoreDsl
            .exec { session -> session.set("taskDone", false) }
            .asLongAs { session -> !(session.getBoolean("taskDone")) }
            .on(
                io.gatling.javaapi.core.CoreDsl
                    .pause(Duration.ofSeconds(1))
                    .exec(
                        http("Poll Task")
                            .get("/api/tasks/#{taskId}")
                            .check(
                                status().shouldBe(200),
                                io.gatling.javaapi.core.CoreDsl
                                    .jsonPath("$.status")
                                    .saveAs("taskStatus"),
                            ),
                    ).exec { session ->
                        val taskStatus = session.getString("taskStatus")
                        val done = taskStatus == "COMPLETED" || taskStatus == "FAILED"
                        session.set("taskDone", done)
                    },
            )

    private val imageProcessScenario =
        scenario("Image Process Workflow")
            .exec(submitTask)
            .exec(pollUntilDone)

    init {
        setUp(
            imageProcessScenario.injectOpen(
                // 5초 대기
                nothingFor(Duration.ofSeconds(5)),
                // 0 → 100 RPS (1분)
                rampUsersPerSec(0.0).to(100.0).during(Duration.ofMinutes(1)),
                // 100 → 500 RPS (2분)
                rampUsersPerSec(100.0).to(500.0).during(Duration.ofMinutes(2)),
                // 500 RPS 유지 (5분)
                constantUsersPerSec(500.0).during(Duration.ofMinutes(5)),
                // 500 → 0 RPS 램프다운 (1분)
                rampUsersPerSec(500.0).to(0.0).during(Duration.ofMinutes(1)),
            ),
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().max().lt(10000),
                global().successfulRequests().percent().gt(95.0),
                details("Create Task").responseTime().percentile3().lt(2000),
                details("Poll Task").responseTime().percentile3().lt(1000),
            )
    }
}
