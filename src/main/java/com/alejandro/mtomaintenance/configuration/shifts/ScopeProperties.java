package com.alejandro.mtomaintenance.configuration.shifts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El ambito por EP ({@code app.scope.*}, {@code MaintenanceScope}): con {@code enabled} (por defecto), un
 * jefe de equipo, un supervisor o un jefe de supervisores con ficha en mto-resources solo escribe en lo de
 * los EP de sus equipos; con {@code false}, nadie tiene limite. Leer nunca se limita.
 *
 * @param enabled si el ambito se aplica ({@code true} por defecto)
 */
@ConfigurationProperties(prefix = "app.scope")
public record ScopeProperties(Boolean enabled) {

    public ScopeProperties {
        enabled = enabled == null || enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
