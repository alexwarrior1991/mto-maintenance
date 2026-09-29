package com.alejandro.mtomaintenance.application.service.impl;

import com.alejandro.mtomaintenance.application.dto.asset.PreventiveDueSoonReport;
import com.alejandro.mtomaintenance.application.service.DomainEventPublisher;
import com.alejandro.mtomaintenance.application.service.PreventiveDueSoonService;
import com.alejandro.mtomaintenance.configuration.events.PreventiveDueSoonProperties;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.infrastructure.persistence.specification.CatenaryAssetSpecification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Publica el aviso diario de preventivos a vencer.
 *
 * <p>Todo en una transaccion: primero un cerrojo de aviso de PostgreSQL ligado a ella
 * ({@code pg_try_advisory_xact_lock}), para que con varias instancias solo una haga la consulta y la
 * publicacion de cada dia; despues la consulta, con la misma regla que el filtro
 * {@code preventiveDueBefore} de la API (vence antes de {@code ahora + horizonte}, o nunca se hizo);
 * y por ultimo el evento, escrito en el outbox con un {@code operationId} derivado de la fecha. El
 * cerrojo evita el trabajo repetido y el identificador evita el aviso repetido si aun asi dos
 * pasadas llegaran a publicar. Sin nada que venza no se publica nada.</p>
 */
@Service
@RequiredArgsConstructor
class PreventiveDueSoonServiceImpl implements PreventiveDueSoonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreventiveDueSoonServiceImpl.class);

    /** Clave del cerrojo de aviso, propia de este trabajo; cualquier otro trabajo usa otra. */
    static final long LOCK_KEY = "mto-maintenance:preventive-due-soon".hashCode();

    static final String OPERATION_ID_PREFIX = "preventive-due-soon:";

    private final CatenaryAssetRepository repository;
    private final DomainEventPublisher events;
    private final PreventiveDueSoonProperties properties;

    @Override
    @Transactional
    public PreventiveDueSoonReport publishDueSoon() {
        return publishDueSoon(Instant.now());
    }

    /** El mismo trabajo con el instante dado: la fecha del evento es la UTC de ese instante. */
    @Transactional
    public PreventiveDueSoonReport publishDueSoon(Instant now) {
        LocalDate date = LocalDate.ofInstant(now, ZoneOffset.UTC);
        int horizonDays = properties.horizonDays();

        if (!repository.tryAdvisoryTransactionLock(LOCK_KEY)) {
            LOGGER.info("Preventive due-soon check of {} skipped: another instance holds the lock", date);
            return new PreventiveDueSoonReport(date, horizonDays, 0, 0, false);
        }

        Instant before = now.plus(Duration.ofDays(horizonDays));
        List<MaintenanceEvents.DueAsset> due = repository.findAll(
                        CatenaryAssetSpecification.enabledEquals(true)
                                .and(CatenaryAssetSpecification.preventiveDueBefore(before)),
                        Sort.by("code"))
                .stream()
                .map(asset -> dueAsset(asset, now))
                .sorted(Comparator.comparing((MaintenanceEvents.DueAsset asset) -> asset.dueAt() == null ? Instant.MIN : asset.dueAt())
                        .thenComparing(MaintenanceEvents.DueAsset::code))
                .toList();
        int overdue = (int) due.stream().filter(MaintenanceEvents.DueAsset::overdue).count();

        if (due.isEmpty()) {
            LOGGER.info("Preventive due-soon check of {}: nothing due within {} days", date, horizonDays);
            return new PreventiveDueSoonReport(date, horizonDays, 0, 0, false);
        }

        events.publish(operationId(date), MaintenanceEvents.preventiveDueSoon(date, horizonDays, due, properties.sampleSize()));
        LOGGER.info("Preventive due-soon check of {}: {} assets due within {} days, {} overdue", date, due.size(), horizonDays, overdue);
        return new PreventiveDueSoonReport(date, horizonDays, due.size(), overdue, true);
    }

    /** El mismo identificador para el mismo dia, en cualquier instancia: la clave de idempotencia del consumidor. */
    static UUID operationId(LocalDate date) {
        return UUID.nameUUIDFromBytes((OPERATION_ID_PREFIX + date).getBytes(StandardCharsets.UTF_8));
    }

    private static MaintenanceEvents.DueAsset dueAsset(CatenaryAsset asset, Instant now) {
        Instant last = asset.getLastPreventiveCompletedAt();
        Instant dueAt = last == null ? null : last.plus(Duration.ofDays(asset.getPreventiveIntervalDays()));
        boolean overdue = dueAt == null || !dueAt.isAfter(now);
        return new MaintenanceEvents.DueAsset(asset.getId(), asset.getCode(), asset.getName(), asset.getType(), asset.getTrackId(),
                asset.getExecutionPackageId(), last, dueAt, overdue);
    }
}
