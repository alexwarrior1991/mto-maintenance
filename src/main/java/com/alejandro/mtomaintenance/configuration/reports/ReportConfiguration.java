package com.alejandro.mtomaintenance.configuration.reports;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra {@link ReportProperties}; no depende de ningun interruptor, los informes existen siempre. */
@Configuration
@EnableConfigurationProperties(ReportProperties.class)
public class ReportConfiguration {
}
