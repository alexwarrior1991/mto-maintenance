package com.alejandro.mtomaintenance.infrastructure.persistence.audit;

import com.alejandro.mtomaintenance.configuration.JpaAuditingConfiguration;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.repository.CatenaryAssetRepository;
import com.alejandro.mtomaintenance.support.PostgreSQLTestContainer;
import org.hibernate.envers.RevisionType;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Historial de cambios de punta a punta. Desactiva la transaccion del test a proposito: Envers
 * escribe al completar la transaccion, no al hacer flush, y un slice test con rollback no registraria
 * nada y pareceria que Envers esta roto.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(JpaAuditingConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EnversAuditDataJpaTest extends PostgreSQLTestContainer {

    @DynamicPropertySource
    static void postgreSQLProperties(DynamicPropertyRegistry registry) {
        registerPostgreSQLProperties(registry);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CatenaryAssetRepository assetRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updatingAnOrderRecordsARevisionWithTheAuthenticatedUsername() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("preferred_username", "alejandro").build(),
                AuthorityUtils.createAuthorityList("ROLE_MAINTENANCE_WRITE")));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID orderId = inTransaction(em -> {
            CatenaryAsset asset = CatenaryAsset.builder().code("SEC-AUD-" + suffix).name("Section").type(CatenaryAssetType.TRACK_SECTION)
                    .trackId(2L).startKp(new BigDecimal("100.000")).endKp(new BigDecimal("200.000")).trackKind(TrackKind.MAIN).build();
            em.persist(asset);
            MaintenanceOrder order = MaintenanceOrder.builder().code("MO-AUD-" + suffix).title("Before").type(MaintenanceOrderType.CORRECTIVE)
                    .priority(MaintenancePriority.LOW).asset(asset).build();
            em.persist(order);
            return order.getId();
        });

        inTransaction(em -> {
            MaintenanceOrder order = em.find(MaintenanceOrder.class, orderId);
            order.setTitle("After");
            order.setPriority(MaintenancePriority.HIGH);
            return null;
        });

        List<Number> revisions = reading(reader -> reader.getRevisions(MaintenanceOrder.class, orderId));
        assertEquals(2, revisions.size());
        assertEquals("Before", reading(reader -> reader.find(MaintenanceOrder.class, orderId, revisions.get(0))).getTitle());
        MaintenanceOrder current = reading(reader -> reader.find(MaintenanceOrder.class, orderId, revisions.get(1)));
        assertEquals("After", current.getTitle());
        assertEquals(MaintenancePriority.HIGH, current.getPriority());
        AuditRevision revision = reading(reader -> reader.findRevision(AuditRevision.class, revisions.get(1)));
        assertEquals("alejandro", revision.getUsername());
    }

    @Test
    void theStatusHistoryAndTheCataloguesHaveNoAuditTwinsWhileTheAuditedTablesDo() {
        List<String> tables = inTransaction(em -> em.createNativeQuery(
                "select table_name from information_schema.tables where table_schema = 'public' and table_name like '%_aud'", String.class).getResultList());

        assertTrue(tables.containsAll(List.of("catenary_asset_aud", "maintenance_order_aud", "maintenance_task_aud", "maintenance_shift_aud",
                "maintenance_inspection_aud", "catenary_defect_aud", "maintenance_material_usage_aud", "maintenance_task_task_type_aud",
                "maintenance_shift_track_aud")));
        assertFalse(tables.contains("maintenance_status_history_aud"));
        assertFalse(tables.contains("maintenance_task_type_aud"));
        assertFalse(tables.contains("inbox_message_aud"));
    }

    private <T> T inTransaction(Function<EntityManager, T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.apply(entityManager));
    }

    private <T> T reading(Function<AuditReader, T> work) {
        return inTransaction(em -> work.apply(AuditReaderFactory.get(em)));
    }

    @Test
    void masterDataUpsertsLeaveNoRevisionWhileAnApiEditOfTheSameAssetDoes() {
        String sourceId = "prf-" + UUID.randomUUID();
        inTransaction(em -> assetRepository.upsertFromMasterData("mto-configuration", sourceId, "PRF-" + sourceId, "12-2.27", "PROFILE",
                6L, 2L, null, new BigDecimal("12847.990"), new BigDecimal("12847.990"), null, "A/S", true, 10L));
        UUID assetId = inTransaction(em -> assetRepository.findBySourceServiceAndSourceEntityId("mto-configuration", sourceId).orElseThrow().getId());

        assertTrue(reading(reader -> reader.getRevisions(CatenaryAsset.class, assetId)).isEmpty(), "Native SQL bypasses Envers on purpose");

        inTransaction(em -> assetRepository.upsertFromMasterData("mto-configuration", sourceId, "PRF-" + sourceId, "12-2.27 renamed", "PROFILE",
                6L, 2L, null, new BigDecimal("12847.990"), new BigDecimal("12847.990"), null, "A/S", true, 11L));
        assertTrue(reading(reader -> reader.getRevisions(CatenaryAsset.class, assetId)).isEmpty());

        inTransaction(em -> {
            CatenaryAsset asset = em.find(CatenaryAsset.class, assetId);
            asset.setDescription("Edited through the API");
            asset.setPreventiveIntervalDays(180);
            return null;
        });

        List<Number> revisions = reading(reader -> reader.getRevisions(CatenaryAsset.class, assetId));
        assertEquals(1, revisions.size());
        CatenaryAsset audited = reading(reader -> reader.find(CatenaryAsset.class, assetId, revisions.getFirst()));
        assertEquals("Edited through the API", audited.getDescription());
        assertEquals("12-2.27 renamed", audited.getName(), "The revision snapshots the row as the event left it");
        List<Object[]> rows = reading(reader -> {
            @SuppressWarnings("unchecked")
            List<Object[]> result = reader.createQuery().forRevisionsOfEntity(CatenaryAsset.class, false, true)
                    .add(org.hibernate.envers.query.AuditEntity.id().eq(assetId)).getResultList();
            return result;
        });
        assertEquals(RevisionType.MOD, rows.getFirst()[2], "Without an ADD revision the first API edit is recorded as a modification");
        assertEquals("system", ((AuditRevision) rows.getFirst()[1]).getUsername(), "No authenticated user: the system is the author");
    }
}
