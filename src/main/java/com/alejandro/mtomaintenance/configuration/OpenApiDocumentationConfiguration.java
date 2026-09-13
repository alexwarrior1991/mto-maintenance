package com.alejandro.mtomaintenance.configuration;

import com.alejandro.mtomaintenance.application.dto.error.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Metadatos, esquema de seguridad, etiquetas y respuestas de error reutilizables del OpenAPI.
 *
 * <p>A diferencia de {@code mto-stock}, las rutas no se declaran aqui a mano: springdoc las lee de
 * las anotaciones de los controladores, que son la unica fuente de verdad de la API. Lo que si se
 * fija aqui es lo transversal, que ningun controlador deberia repetir.</p>
 */
@Configuration
public class OpenApiDocumentationConfiguration {

    private static final String JSON = "application/json";
    static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI mtoMaintenanceOpenApi() {
        return new OpenAPI()
                .info(apiInfo())
                // Requisito global: cada operacion documentada pide el bearer salvo que lo anule
                // explicitamente, de modo que anadir un endpoint nuevo no lo publica como abierto.
                .security(List.of(new SecurityRequirement().addList(BEARER_AUTH)))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Local development server"),
                        new Server().url("http://localhost:8090/api/maintenance").description("Through mto-gateway")
                ))
                .tags(tags())
                .components(components());
    }

    private static Info apiInfo() {
        return new Info()
                .title("Catenary Maintenance API")
                .description("Preventive and corrective maintenance of the overhead contact system (OCS): "
                        + "maintainable assets, maintenance orders and tasks, shifts, inspections, defects, "
                        + "material usage and progress reports.")
                .version("v1")
                .contact(new Contact()
                        .name("MTO Platform Team")
                        .email("support@example.com"))
                .license(new License()
                        .name("Proprietary"));
    }

    private static List<Tag> tags() {
        return List.of(
                tag("Assets", "Maintainable catenary assets: track sections, profiles, disconnectors and section insulators."),
                tag("Orders", "Maintenance orders and their lifecycle: plan, assign, start, complete, cancel."),
                tag("Tasks", "Tasks of an order, one per profile in preventive maintenance, with their check items."),
                tag("Materials", "Materials planned and consumed by an order, synchronized with mto-stock."),
                tag("Shifts", "Night shifts: the unit of execution and the source of the daily report."),
                tag("Teams", "Maintenance teams, their base and vehicle."),
                tag("Task Types", "Catalogue of RG/RP task types with standard times and execution windows."),
                tag("Inspections", "Visual and technical inspections with per-item measurements."),
                tag("Inspection Templates", "Inspection checklists per asset type."),
                tag("Defects", "Detected defects and their lifecycle until closure."),
                tag("Reports", "Progress, daily shift report and monthly summary.")
        );
    }

    private static Components components() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Keycloak-issued JWT access token. Send it as \"Authorization: Bearer <token>\"."))
                .addResponses("BadRequest", errorResponse("Invalid request, validation failure or malformed JSON.", HttpStatus.BAD_REQUEST))
                .addResponses("Unauthorized", errorResponse("Missing, expired or otherwise invalid bearer token.", HttpStatus.UNAUTHORIZED))
                .addResponses("Forbidden", errorResponse("The authenticated user lacks the role required by this operation.", HttpStatus.FORBIDDEN))
                .addResponses("NotFound", errorResponse("The requested resource or business aggregate was not found.", HttpStatus.NOT_FOUND))
                .addResponses("Conflict", errorResponse("The request conflicts with current state: duplicate code, invalid transition, disabled asset or possession window.", HttpStatus.CONFLICT))
                .addResponses("UnprocessableContent", errorResponse("The request is syntactically valid but violates a domain rule.", HttpStatus.UNPROCESSABLE_CONTENT))
                .addResponses("ServiceUnavailable", errorResponse("mto-stock did not answer an explicit synchronization request.", HttpStatus.SERVICE_UNAVAILABLE))
                .addResponses("InternalServerError", errorResponse("Unexpected server error. Internal details are not exposed to clients.", HttpStatus.INTERNAL_SERVER_ERROR));
        Map<String, Schema> schemas = ModelConverters.getInstance().readAll(ApiErrorResponse.class);
        schemas.forEach(components::addSchemas);
        return components;
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private static ApiResponse errorResponse(String description, HttpStatus status) {
        return new ApiResponse()
                .description(status.value() + " - " + description)
                .content(new Content().addMediaType(JSON, new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
    }
}
