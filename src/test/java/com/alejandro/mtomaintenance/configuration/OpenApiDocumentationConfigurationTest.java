package com.alejandro.mtomaintenance.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiDocumentationConfigurationTest {

    private final OpenAPI openAPI = new OpenApiDocumentationConfiguration().mtoMaintenanceOpenApi();

    @Test
    void openApiMetadataServersTagsAndBearerRequirementAreConfigured() {
        assertEquals("Catenary Maintenance API", openAPI.getInfo().getTitle());
        assertEquals("v1", openAPI.getInfo().getVersion());
        assertEquals(2, openAPI.getServers().size());
        assertTrue(openAPI.getTags().stream().anyMatch(tag -> tag.getName().equals("Orders")));
        assertTrue(openAPI.getTags().stream().anyMatch(tag -> tag.getName().equals("Shifts")));
        assertTrue(openAPI.getComponents().getSecuritySchemes().containsKey("bearerAuth"));
        // Requisito global, no marcador de posicion: un endpoint nuevo se documenta como protegido
        // salvo que lo anule explicitamente.
        assertEquals(1, openAPI.getSecurity().size());
        assertTrue(openAPI.getSecurity().getFirst().containsKey("bearerAuth"));
    }

    @Test
    void reusableErrorResponsesAndErrorSchemaAreConfigured() {
        assertTrue(openAPI.getComponents().getSchemas().containsKey("ApiErrorResponse"));
        assertTrue(openAPI.getComponents().getResponses().containsKey("BadRequest"));
        assertTrue(openAPI.getComponents().getResponses().containsKey("Unauthorized"));
        assertTrue(openAPI.getComponents().getResponses().containsKey("Forbidden"));
        assertTrue(openAPI.getComponents().getResponses().containsKey("Conflict"));
        assertTrue(openAPI.getComponents().getResponses().containsKey("UnprocessableContent"));
        assertTrue(openAPI.getComponents().getResponses().containsKey("ServiceUnavailable"));
        assertTrue(openAPI.getComponents().getResponses().containsKey("InternalServerError"));
    }
}
