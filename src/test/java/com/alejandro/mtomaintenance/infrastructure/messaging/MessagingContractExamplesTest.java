package com.alejandro.mtomaintenance.infrastructure.messaging;

import com.alejandro.mtomaintenance.application.dto.messaging.AsynchronousMessage;
import com.alejandro.mtomaintenance.application.dto.messaging.DomainEvent;
import com.alejandro.mtomaintenance.application.dto.messaging.MessageActor;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.service.impl.MaintenanceEvents;
import com.alejandro.mtomaintenance.infrastructure.messaging.outbox.AsynchronousMessageFactory;
import com.alejandro.mtomaintenance.infrastructure.messaging.outbox.AsynchronousMessageHashService;
import com.alejandro.mtomaintenance.infrastructure.messaging.outbox.MessageContextResolver;
import com.alejandro.mtomaintenance.infrastructure.messaging.rabbitmq.MaintenanceRabbitMqNames;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAsset;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryAssetType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CatenaryDefect;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.CheckItemResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectSeverity;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.DefectStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionKind;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionResult;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.InspectionTemplateItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspection;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceInspectionItem;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceMaterialUsage;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrderType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenancePriority;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceShift;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceTeam;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.PossessionType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.ShiftStatus;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.StockRequestType;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.TrackKind;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Los ejemplos de {@code docs/messaging/examples} son el contrato tal como lo ven los consumidores:
 * {@code mto-notification} los copia como fixtures. Un ejemplo escrito a mano se desalinea del codigo
 * sin que nadie lo note; este test lo evita construyendo cada mensaje con {@code MaintenanceEvents}
 * y la factoria real y comparandolo con el fichero, salvo las dos claves que cambian en cada
 * ejecucion (la fecha y, con ella, la huella), que se comprueban aparte: la huella del fichero es
 * la que un consumidor calcularia sobre sus siete claves originales.
 *
 * <p>Un evento nuevo o una clave nueva cambian el ejemplo en el mismo commit. Para regenerarlos:
 * {@code MESSAGING_EXAMPLES_WRITE=true ./mvnw test -Dtest=MessagingContractExamplesTest}, y se
 * revisa el diff como cualquier cambio de contrato.</p>
 */
class MessagingContractExamplesTest {

    private static final Path EXAMPLES = Path.of("docs", "messaging", "examples");

    private static final boolean WRITE = "true".equalsIgnoreCase(System.getenv("MESSAGING_EXAMPLES_WRITE"));

    private static final Instant CREATION_DATE = Instant.parse("2026-09-29T09:00:00Z");

    private static final MessageActor TECHNICIAN = MessageActor.of("6f1b1c8e-0000-4000-8000-000000000031", "mantenimiento.tecnico");
    private static final MessageActor MANAGER = MessageActor.of("6f1b1c8e-0000-4000-8000-000000000032", "mantenimiento.responsable");
    private static final String REQUEST_CORRELATION_ID = "8c3b8c1a-1111-4222-8333-444444444444";

    /** Con decimales como BigDecimal a los dos lados, {@code 12847.990} del fichero es {@code 12847.990} del mensaje. */
    private final ObjectMapper objectMapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final AsynchronousMessageHashService hashService = new AsynchronousMessageHashService(objectMapper);

    // ------------------------------------------------------------------------------- orders

    @Test
    void orderCreated() throws IOException {
        MaintenanceOrder order = urgentOrder();

        check("order-created.json", "a0000000-0000-4000-8000-000000000001", TECHNICIAN, REQUEST_CORRELATION_ID,
                MaintenanceEvents.orderCreated(order, "Order created"));
    }

    @Test
    void orderStatusChanged() throws IOException {
        MaintenanceOrder order = plannedOrder();
        order.setStatus(MaintenanceOrderStatus.ASSIGNED);

        check("order-status-changed.json", "a0000000-0000-4000-8000-000000000002", MANAGER, REQUEST_CORRELATION_ID,
                MaintenanceEvents.orderStatusChanged(order, "PLANNED", "ASSIGNED", "week 40"));
    }

    @Test
    void orderReassigned() throws IOException {
        MaintenanceOrder order = plannedOrder();
        order.setStatus(MaintenanceOrderStatus.IN_PROGRESS);
        order.setActualStartDate(Instant.parse("2026-10-06T21:00:00Z"));
        order.setAssignedUser("yossi");

        check("order-reassigned.json", "a0000000-0000-4000-8000-000000000003", MANAGER, REQUEST_CORRELATION_ID,
                MaintenanceEvents.orderReassigned(order, "Reassigned: night team 2"));
    }

    // ------------------------------------------------------------------------------ defects

    @Test
    void defectCreated() throws IOException {
        check("defect-created.json", "a0000000-0000-4000-8000-000000000004", TECHNICIAN, REQUEST_CORRELATION_ID,
                MaintenanceEvents.defectCreated(defect(inspection()), "Created from inspection INS-000031"));
    }

    @Test
    void defectStatusChanged() throws IOException {
        CatenaryDefect defect = defect(inspection());
        defect.setOrder(plannedOrder());
        defect.setStatus(DefectStatus.IN_PROGRESS);

        check("defect-status-changed.json", "a0000000-0000-4000-8000-000000000005", MANAGER, REQUEST_CORRELATION_ID,
                MaintenanceEvents.defectStatusChanged(defect, "OPEN", "IN_PROGRESS", "Linked to corrective order MO-000123"));
    }

    // -------------------------------------------------------------------------- inspections

    @Test
    void inspectionCreated() throws IOException {
        check("inspection-created.json", "a0000000-0000-4000-8000-000000000006", TECHNICIAN, REQUEST_CORRELATION_ID,
                MaintenanceEvents.inspectionCreated(inspection()));
    }

    @Test
    void inspectionItemFailed() throws IOException {
        MaintenanceInspection inspection = inspection();
        MaintenanceInspectionItem height = inspection.getItems().getFirst();
        height.setMeasuredValue(new BigDecimal("5620"));
        height.setItemResult(CheckItemResult.DEFECT);
        height.setNotes("Above tolerance");

        check("inspection-item-failed.json", "a0000000-0000-4000-8000-000000000007", TECHNICIAN, REQUEST_CORRELATION_ID,
                MaintenanceEvents.inspectionItemFailed(inspection, height));
    }

    @Test
    void inspectionDefectCreated() throws IOException {
        MaintenanceInspection inspection = inspection();

        check("inspection-defect-created.json", "a0000000-0000-4000-8000-000000000008", TECHNICIAN, REQUEST_CORRELATION_ID,
                MaintenanceEvents.inspectionDefectCreated(inspection, defect(inspection)));
    }

    @Test
    void inspectionCorrectiveOrderCreated() throws IOException {
        MaintenanceInspection inspection = inspection();
        CatenaryDefect defect = defect(inspection);
        MaintenanceOrder corrective = MaintenanceOrder.builder().code("MO-000125").title("Corrective work after inspection INS-000031 on PRF-12-2.27")
                .type(MaintenanceOrderType.CORRECTIVE).priority(MaintenancePriority.HIGH).asset(inspection.getAsset())
                .originInspection(inspection).originDefect(defect).build();
        corrective.locateAt(inspection.getAsset());
        id(corrective, "40000000-0000-4000-8000-000000000003");

        check("inspection-corrective-order-created.json", "a0000000-0000-4000-8000-000000000009", MANAGER, REQUEST_CORRELATION_ID,
                MaintenanceEvents.inspectionCorrectiveOrderCreated(inspection, corrective));
    }

    // ------------------------------------------------------------------------------- shifts

    @Test
    void shiftStarted() throws IOException {
        MaintenanceShift shift = shift();
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        shift.setActualStart(Instant.parse("2026-09-29T22:05:00Z"));
        shift.setVoltageCutoffAt(Instant.parse("2026-09-29T22:20:00Z"));

        check("shift-started.json", "a0000000-0000-4000-8000-00000000000a", TECHNICIAN, REQUEST_CORRELATION_ID,
                MaintenanceEvents.shiftStarted(shift));
    }

    @Test
    void shiftClosed() throws IOException {
        MaintenanceShift shift = shift();
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setActualStart(Instant.parse("2026-09-29T22:05:00Z"));
        shift.setVoltageCutoffAt(Instant.parse("2026-09-29T22:20:00Z"));
        shift.setActualEnd(Instant.parse("2026-09-30T03:50:00Z"));
        shift.setNetWorkMinutes(330);

        check("shift-closed.json", "a0000000-0000-4000-8000-00000000000b", TECHNICIAN, REQUEST_CORRELATION_ID,
                MaintenanceEvents.shiftClosed(shift));
    }

    // ---------------------------------------------------------------------------- materials

    @Test
    void materialRejected() throws IOException {
        MaintenanceMaterialUsage line = materialLine();
        line.markRejected("reserve: Insufficient stock for material GA70 in warehouse c0000000-0000-4000-8000-000000000001");

        check("material-rejected.json", "a0000000-0000-4000-8000-00000000000c", TECHNICIAN, REQUEST_CORRELATION_ID,
                MaintenanceEvents.materialRejected(line, "reserve", new StockRejectedException(
                        "Insufficient stock for material GA70 in warehouse c0000000-0000-4000-8000-000000000001", 409, "STK-001", null)));
    }

    @Test
    void materialFailed() throws IOException {
        MaintenanceMaterialUsage line = materialLine();
        line.markFailed("resolve project: mto-stock is unavailable: connection refused");

        check("material-failed.json", "a0000000-0000-4000-8000-00000000000d", TECHNICIAN, REQUEST_CORRELATION_ID,
                MaintenanceEvents.materialFailed(line, "resolve project", new StockUnavailableException("mto-stock is unavailable: connection refused")));
    }

    @Test
    void materialInDoubt() throws IOException {
        MaintenanceMaterialUsage line = materialLine();
        line.markInDoubt(StockRequestType.RESERVATION);
        line.markFailed("reserve: Read timed out");

        // El reintento automatico corre sin peticion ni mensaje: sin actor ni correlacion.
        check("material-in-doubt.json", "a0000000-0000-4000-8000-00000000000e", MessageActor.system(), null,
                MaintenanceEvents.materialInDoubt(line, "reserve", new StockUnavailableException("Read timed out")));
    }

    // ------------------------------------------------------------------------------- assets

    @Test
    void assetDisabled() throws IOException {
        CatenaryAsset profile = profile();
        profile.disableLocally();

        check("asset-disabled.json", "a0000000-0000-4000-8000-00000000000f", MANAGER, REQUEST_CORRELATION_ID,
                MaintenanceEvents.assetDisabled(profile));
    }

    // --------------------------------------------------------------------------- preventives

    @Test
    void preventiveDueSoon() throws IOException {
        LocalDate date = LocalDate.of(2026, 9, 29);
        CatenaryAsset section = section();
        CatenaryAsset profile = profile();
        List<MaintenanceEvents.DueAsset> due = List.of(
                new MaintenanceEvents.DueAsset(section.getId(), section.getCode(), section.getName(), section.getType(), section.getTrackId(),
                        section.getExecutionPackageId(), Instant.parse("2025-09-27T00:00:00Z"), Instant.parse("2026-09-27T00:00:00Z"), true),
                new MaintenanceEvents.DueAsset(profile.getId(), profile.getCode(), profile.getName(), profile.getType(), profile.getTrackId(),
                        profile.getExecutionPackageId(), Instant.parse("2025-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"), false));

        // Un trabajo programado: sin persona ni peticion detras, y con el operationId derivado de la fecha.
        UUID operationId = UUID.nameUUIDFromBytes("preventive-due-soon:2026-09-29".getBytes(StandardCharsets.UTF_8));
        check("preventive-due-soon.json", operationId.toString(), MessageActor.system(), null,
                MaintenanceEvents.preventiveDueSoon(date, 7, due, 20));
    }

    // ----------------------------------------------------------------------------- fixtures

    private static CatenaryAsset profile() {
        CatenaryAsset asset = CatenaryAsset.builder().code("PRF-12-2.27").name("12-2.27").type(CatenaryAssetType.PROFILE)
                .executionPackageId(6L).trackId(2L).startKp(new BigDecimal("12847.990")).endKp(new BigDecimal("12847.990"))
                .sourceService("mto-configuration").sourceEntityId("4711").enabledAtSource(true).preventiveIntervalDays(365)
                .lastPreventiveCompletedAt(Instant.parse("2025-10-01T00:00:00Z")).build();
        id(asset, "20000000-0000-4000-8000-000000000001");
        return asset;
    }

    private static CatenaryAsset section() {
        CatenaryAsset asset = CatenaryAsset.builder().code("SEC-T2").name("Ranana-Herzliya T2").type(CatenaryAssetType.TRACK_SECTION)
                .executionPackageId(6L).trackId(2L).startKp(new BigDecimal("12847.990")).endKp(new BigDecimal("14078.090"))
                .trackKind(TrackKind.MAIN).preventiveIntervalDays(365).lastPreventiveCompletedAt(Instant.parse("2025-09-27T00:00:00Z")).build();
        id(asset, "20000000-0000-4000-8000-000000000002");
        return asset;
    }

    private static MaintenanceTeam team() {
        MaintenanceTeam team = MaintenanceTeam.builder().code("EQ-1").name("Equipo noche 1").baseName("Base Herzliya").vehicle("Dresina 3").build();
        id(team, "30000000-0000-4000-8000-000000000001");
        return team;
    }

    private static MaintenanceOrder plannedOrder() {
        CatenaryAsset section = section();
        MaintenanceOrder order = MaintenanceOrder.builder().code("MO-000123").title("Preventive T2 week 40").type(MaintenanceOrderType.PREVENTIVE)
                .priority(MaintenancePriority.MEDIUM).status(MaintenanceOrderStatus.PLANNED).asset(section).plannedDate(LocalDate.of(2026, 10, 6))
                .team(team()).assignedUser("mantenimiento.tecnico").stockProjectId(UUID.fromString("50000000-0000-4000-8000-000000000001")).build();
        order.locateAt(section);
        id(order, "40000000-0000-4000-8000-000000000001");
        return order;
    }

    private static MaintenanceOrder urgentOrder() {
        CatenaryAsset profile = profile();
        MaintenanceOrder order = MaintenanceOrder.builder().code("MO-000124").title("Broken contact wire at kp 12+848").type(MaintenanceOrderType.URGENT)
                .priority(MaintenancePriority.CRITICAL).status(MaintenanceOrderStatus.DRAFT).asset(profile).build();
        order.locateAt(profile);
        id(order, "40000000-0000-4000-8000-000000000002");
        return order;
    }

    private static MaintenanceInspection inspection() {
        CatenaryAsset profile = profile();
        MaintenanceInspection inspection = MaintenanceInspection.builder().code("INS-000031").asset(profile).executionPackageId(6L).trackId(2L)
                .kp(new BigDecimal("12847.990")).inspectionDate(LocalDate.of(2026, 9, 28)).inspector("mantenimiento.tecnico")
                .inspectionKind(InspectionKind.VISUAL).result(InspectionResult.MAJOR_DEFECT).description("Weekly visual check")
                .detectedDefects("Cracked insulator").recommendedActions("Replace the insulator").build();
        id(inspection, "70000000-0000-4000-8000-000000000001");
        MaintenanceInspectionItem height = MaintenanceInspectionItem.fromTemplate(inspection, InspectionTemplateItem.builder()
                .code("CW_HEIGHT").label("Contact wire height").unit("mm").minValue(new BigDecimal("5000")).maxValue(new BigDecimal("5500"))
                .requiresMeasure(true).build());
        id(height, "80000000-0000-4000-8000-000000000001");
        MaintenanceInspectionItem stagger = MaintenanceInspectionItem.fromTemplate(inspection, InspectionTemplateItem.builder()
                .code("STAGGER").label("Stagger").unit("mm").minValue(new BigDecimal("-300")).maxValue(new BigDecimal("300"))
                .requiresMeasure(true).build());
        id(stagger, "80000000-0000-4000-8000-000000000002");
        inspection.getItems().add(height);
        inspection.getItems().add(stagger);
        return inspection;
    }

    private static CatenaryDefect defect(MaintenanceInspection inspection) {
        CatenaryDefect defect = CatenaryDefect.builder().code("DEF-000045").asset(inspection.getAsset()).inspection(inspection)
                .severity(DefectSeverity.HIGH).status(DefectStatus.OPEN).description("Cracked insulator").technicalNotes("Replace the insulator")
                .detectedAt(Instant.parse("2026-09-28T00:00:00Z")).repairPlannedDate(LocalDate.of(2026, 10, 10)).build();
        defect.locateAt(inspection.getAsset());
        id(defect, "60000000-0000-4000-8000-000000000001");
        return defect;
    }

    private static MaintenanceShift shift() {
        MaintenanceShift shift = MaintenanceShift.builder().code("SH-000077").shiftDate(LocalDate.of(2026, 9, 29)).team(team())
                .baseName("Base Herzliya").vehicle("Dresina 3").possessionType(PossessionType.PARTIAL)
                .plannedStart(Instant.parse("2026-09-29T22:00:00Z")).plannedEnd(Instant.parse("2026-09-30T04:00:00Z"))
                .executionPackageId(6L).trackIds(new LinkedHashSet<>(Set.of(2L))).startKp(new BigDecimal("12847.990")).endKp(new BigDecimal("14078.090"))
                .build();
        id(shift, "90000000-0000-4000-8000-000000000001");
        return shift;
    }

    private static MaintenanceMaterialUsage materialLine() {
        MaintenanceMaterialUsage line = MaintenanceMaterialUsage.builder().order(plannedOrder())
                .materialId(UUID.fromString("b0000000-0000-4000-8000-000000000001")).materialCode("GA70").materialDescriptionSnapshot("Grapa de atirantado 70")
                .warehouseId(UUID.fromString("c0000000-0000-4000-8000-000000000001")).plannedQuantity(new BigDecimal("4")).unit("ud").build();
        id(line, "a1000000-0000-4000-8000-000000000001");
        return line;
    }

    private static void id(Object entity, String id) {
        ReflectionTestUtils.setField(entity, "id", UUID.fromString(id));
    }

    // ------------------------------------------------------------------------------ helpers

    private void check(String fileName, String operationId, MessageActor actor, String correlationId, DomainEvent event) throws IOException {
        MessageContextResolver contextResolver = mock(MessageContextResolver.class);
        when(contextResolver.currentActor()).thenReturn(actor);
        when(contextResolver.currentCorrelationId()).thenReturn(correlationId);
        AsynchronousMessageFactory factory = new AsynchronousMessageFactory(hashService, contextResolver, "mto-maintenance");

        AsynchronousMessage<DomainEvent> message = factory.create(UUID.fromString(operationId),
                event.entityName() + "-" + event.entityId(),
                MaintenanceRabbitMqNames.eventType(event.entityName(), event.eventName()), event);

        Path file = EXAMPLES.resolve(fileName);
        if (WRITE) {
            Files.createDirectories(EXAMPLES);
            Files.writeString(file, pretty(tree(withCreationDate(message, CREATION_DATE)), 0) + "\n");
        }

        assertThat(file).as("el ejemplo %s tiene que estar versionado", fileName).exists();
        assertSameAsExample(message, objectMapper.readTree(Files.readString(file)));
    }

    /**
     * Compara el mensaje con el ejemplo por el texto que viaja de verdad (releido como arbol para
     * que el orden de las claves no cuente), y la huella del ejemplo con la que se obtiene de sus
     * propias siete claves, que es la unica forma de que el ejemplo lleve una huella cierta.
     */
    private void assertSameAsExample(AsynchronousMessage<DomainEvent> message, JsonNode example) {
        ObjectNode produced = tree(message);

        assertThat(Instant.parse(produced.get("creationDate").asText())).isNotNull();
        assertThat(produced.get("messageHash").asText()).matches("[0-9a-f]{64}");

        // La fecha es la de ahora y la huella la incluye: se toman las del ejemplo para comparar lo
        // demas, que es lo que el ejemplo fija.
        produced.put("creationDate", example.get("creationDate").asText());
        produced.put("messageHash", example.get("messageHash").asText());

        assertThat(produced).isEqualTo(example);

        AsynchronousMessage<DomainEvent> asInExample = withCreationDate(message, Instant.parse(example.get("creationDate").asText()));
        assertThat(example.get("messageHash").asText())
                .as("la huella del ejemplo es la de sus siete claves originales, con la fecha del ejemplo")
                .isEqualTo(asInExample.messageHash());
    }

    private AsynchronousMessage<DomainEvent> withCreationDate(AsynchronousMessage<DomainEvent> message, Instant creationDate) {
        AsynchronousMessage<DomainEvent> dated = new AsynchronousMessage<>(message.operationId(), message.referenceId(), message.origin(),
                creationDate, message.eventType(), message.data(), "PENDING", message.actor(), message.correlationId());
        return dated.withMessageHash(hashService.calculate(dated));
    }

    private ObjectNode tree(AsynchronousMessage<DomainEvent> message) {
        return (ObjectNode) objectMapper.readTree(objectMapper.writeValueAsString(message));
    }

    /** Dos espacios, una clave por linea y los elementos de una lista tambien: un diff legible en una revision. */
    private static String pretty(JsonNode node, int depth) {
        String pad = "  ".repeat(depth + 1);
        if (node.isObject()) {
            if (node.isEmpty()) {
                return "{}";
            }
            List<String> fields = new ArrayList<>();
            node.properties().forEach(field -> fields.add(pad + "\"" + field.getKey() + "\": " + pretty(field.getValue(), depth + 1)));
            return "{\n" + String.join(",\n", fields) + "\n" + "  ".repeat(depth) + "}";
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                return "[]";
            }
            List<String> items = new ArrayList<>();
            node.forEach(item -> items.add(pad + pretty(item, depth + 1)));
            return "[\n" + String.join(",\n", items) + "\n" + "  ".repeat(depth) + "]";
        }
        return node.toString();
    }
}
