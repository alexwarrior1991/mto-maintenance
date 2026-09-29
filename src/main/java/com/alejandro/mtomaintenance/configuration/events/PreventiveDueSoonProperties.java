package com.alejandro.mtomaintenance.configuration.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The daily preventive due-soon check ({@code app.events.preventive-due-soon.*}).
 *
 * <p>El interruptor ({@code enabled}) no esta aqui: lo lee el {@code @ConditionalOnProperty} de
 * {@link PreventiveDueSoonConfiguration}, que es quien decide si el planificador existe, como con el
 * consumidor de datos maestros. El cron tampoco: lo lee el propio {@code @Scheduled}.</p>
 *
 * @param horizonDays cuantos dias hacia delante se mira (7 por defecto); lo ya vencido entra siempre
 * @param sampleSize  cuantos activos viajan en el evento ademas del recuento (20 por defecto)
 */
@ConfigurationProperties(prefix = "app.events.preventive-due-soon")
public record PreventiveDueSoonProperties(Integer horizonDays, Integer sampleSize) {

    public static final int DEFAULT_HORIZON_DAYS = 7;
    public static final int DEFAULT_SAMPLE_SIZE = 20;

    public PreventiveDueSoonProperties {
        horizonDays = horizonDays == null || horizonDays <= 0 ? DEFAULT_HORIZON_DAYS : horizonDays;
        sampleSize = sampleSize == null || sampleSize < 0 ? DEFAULT_SAMPLE_SIZE : sampleSize;
    }
}
