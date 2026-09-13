package com.alejandro.mtomaintenance;

import com.alejandro.mtomaintenance.application.service.EntityAuditService;
import com.alejandro.mtomaintenance.application.service.InboxMessageService;
import com.alejandro.mtomaintenance.application.service.MasterDataEntityHandler;
import com.alejandro.mtomaintenance.application.service.MasterDataEventHandler;
import com.alejandro.mtomaintenance.application.service.MasterDataEventProcessor;
import com.alejandro.mtomaintenance.support.PostgreSQLTestContainer;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * El unico {@code @SpringBootTest}: arranca el contexto entero contra un PostgreSQL real sin
 * sustituir ningun servicio por un mock. Es lo que detecta un {@code @Service} que no llega a ser
 * bean (un {@code @ConditionalOnBean} en una clase escaneada, por ejemplo) antes de empaquetar.
 */
@SpringBootTest(properties = {
        // No hay broker ni mto-stock en este test. El cableado del canal se comprueba en
        // MessagingLayerTest y el cliente de stock en StockClientTest, ninguno de los dos los necesita.
        "app.rabbitmq.enabled=false",
        "app.stock.enabled=false"
})
class MtoMaintenanceApplicationTests extends PostgreSQLTestContainer {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerPostgreSQLProperties(registry);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired(required = false)
    private MasterDataEventHandler masterDataEventHandler;

    @Autowired(required = false)
    private List<MasterDataEntityHandler> masterDataEntityHandlers;

    @Autowired(required = false)
    private Tracer tracer;

    @Test
    void contextLoads() {
        assertNotNull(masterDataEventHandler);
    }

    @Test
    void everyBusinessServiceIsInTheContext() {
        for (Class<?> service : BusinessServices.ALL) {
            assertThat(context.getBeanNamesForType(service))
                    .withFailMessage("""
                            No hay ningun bean de %s en el contexto. Los controladores lo piden por \
                            constructor, asi que la aplicacion no arranca. Comprobar que su impl \
                            sigue anotado con @Service y que no ha vuelto un @ConditionalOnBean: \
                            esa anotacion solo vale en autoconfiguraciones y aqui es siempre falsa.""",
                            service.getSimpleName())
                    .isNotEmpty();
        }
    }

    @Test
    void theMasterDataHandlersAreRegistered() {
        assertThat(masterDataEntityHandlers)
                .withFailMessage("No hay ningun MasterDataEntityHandler en el contexto")
                .isNotNull();
        assertThat(masterDataEntityHandlers)
                .extracting(MasterDataEntityHandler::entityName)
                .containsAll(BusinessServices.HANDLED_ENTITIES);
    }

    @Test
    void theTracingBridgeIsInTheContext() {
        assertNotNull(tracer);
    }

    /** Lista viva: cada fase que anade un servicio lo anade aqui. */
    static final class BusinessServices {
        static final List<Class<?>> ALL = List.of(
                EntityAuditService.class,
                InboxMessageService.class,
                MasterDataEventProcessor.class);

        static final List<String> HANDLED_ENTITIES = List.of();

        private BusinessServices() {
        }
    }
}
