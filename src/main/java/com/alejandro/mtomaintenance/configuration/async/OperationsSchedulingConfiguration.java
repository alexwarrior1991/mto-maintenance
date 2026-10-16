package com.alejandro.mtomaintenance.configuration.async;

import com.alejandro.mtomaintenance.application.service.BudgetedOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * El latido, el abandono y la purga de las operaciones de fondo. Con {@code spring.threads.virtual.enabled}
 * cada pasada corre en un hilo virtual. Apagado en los tests, que llaman a los metodos a mano.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.operations", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class OperationsSchedulingConfiguration {

    private final BudgetedOperations operations;

    @Scheduled(fixedDelayString = "${app.operations.heartbeat:5s}")
    public void heartbeat() {
        operations.heartbeat();
    }

    @Scheduled(fixedDelayString = "${app.operations.abandon-check:30s}")
    public void failAbandoned() {
        operations.failAbandoned();
    }

    @Scheduled(fixedDelayString = "${app.operations.purge-every:1h}")
    public void purgeFinished() {
        operations.purgeFinished();
    }
}
