package com.example.imageprocess.application

import com.example.imageprocess.domain.model.Task
import com.example.imageprocess.domain.model.TaskEvent
import com.example.imageprocess.domain.model.TaskEvent.Complete
import com.example.imageprocess.domain.model.TaskEvent.Fail
import com.example.imageprocess.domain.model.TaskEvent.RecoverToSubmitted
import com.example.imageprocess.domain.model.TaskEvent.StartProcessing
import com.example.imageprocess.domain.model.TaskEvent.Submit
import com.example.imageprocess.domain.model.TaskStatus
import com.example.imageprocess.domain.statemachine.core.StateMachine
import com.example.imageprocess.domain.statemachine.dsl.stateMachine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TaskStateMachineConfig {
    @Bean
    fun taskStateMachine(): StateMachine<TaskStatus, TaskEvent, Task> =
        stateMachine {
            from(TaskStatus.PENDING) {
                on<Submit>() goto TaskStatus.SUBMITTED action { task, event ->
                    task.withJobId(event.jobId)
                }
                on<Fail>() goto TaskStatus.FAILED action { task, event ->
                    task.withFailReason(event.reason).withNextPoll(null)
                }
            }

            from(TaskStatus.SUBMITTED) {
                on<StartProcessing>() goto TaskStatus.PROCESSING
                on<Fail>() goto TaskStatus.FAILED action { task, event ->
                    task.withFailReason(event.reason).withNextPoll(null)
                }
                on<TaskEvent.RetryWait>() goto TaskStatus.RETRY_WAITING
            }

            from(TaskStatus.PROCESSING) {
                on<Complete>() goto TaskStatus.COMPLETED action { task, event ->
                    task.withResult(event.result).withNextPoll(null)
                }
                on<Fail>() goto TaskStatus.FAILED action { task, event ->
                    task.withFailReason(event.reason).withNextPoll(null)
                }
                on<TaskEvent.RetryWait>() goto TaskStatus.RETRY_WAITING
            }

            from(TaskStatus.RETRY_WAITING) {
                on<RecoverToSubmitted>() goto TaskStatus.SUBMITTED
                on<Fail>() goto TaskStatus.FAILED action { task, event ->
                    task.withFailReason(event.reason).withNextPoll(null)
                }
            }
        }
}
