package com.alejandro.mtomaintenance.configuration.events;

import com.alejandro.mtomaintenance.application.service.PreventiveDueSoonService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Registers the properties of the daily preventive check and, when it is on, its scheduler.
 *
 * <p>Las properties se registran siempre porque el servicio que hace el trabajo existe siempre (los
 * tests lo llaman); el planificador solo con {@code app.rabbitmq.enabled} —sin broker no hay a quien
 * contarselo— y con {@code app.events.preventive-due-soon.enabled}. Con varias instancias, todas
 * despiertan a la misma hora y el cerrojo de aviso decide cual trabaja.</p>
 */
@Configuration
@EnableConfigurationProperties(PreventiveDueSoonProperties.class)
public class PreventiveDueSoonConfiguration {

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "app.events.preventive-due-soon", name = "enabled", havingValue = "true", matchIfMissing = true)
    @RequiredArgsConstructor
    static class Scheduler {

        private static final Logger LOGGER = LoggerFactory.getLogger(Scheduler.class);

        private final PreventiveDueSoonService preventiveDueSoonService;

        @Scheduled(cron = "${app.events.preventive-due-soon.cron:0 7 6 * * *}")
        public void publishDueSoon() {
            try {
                preventiveDueSoonService.publishDueSoon();
            } catch (RuntimeException exception) {
                // Manana vuelve a intentarse; un fallo aqui no puede tumbar el planificador que
                // comparte con el outbox y el reintento de stock.
                LOGGER.error("The preventive due-soon check failed", exception);
            }
        }
    }
}
