package com.alejandro.mtomaintenance.configuration.shifts;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ShiftProperties.class, ScopeProperties.class})
public class ShiftsConfiguration {
}
