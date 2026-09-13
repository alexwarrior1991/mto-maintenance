package com.alejandro.mtomaintenance.configuration.security;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprobaciones unitarias de las piezas de seguridad que no necesitan cadena de filtros: la
 * traducción de claims a autoridades, la validación de audiencia, la lectura del usuario actual y
 * las invariantes de configuración. Las reglas por ruta y verbo se prueban en
 * {@link ApiAuthorizationRulesTest}.
 */
class SecurityLayerTest {

    private static final String CLIENT_ID = "mto-maintenance-api";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientRolesBecomeRolePrefixedAuthoritiesNormalizedToUpperCase() {
        AbstractAuthenticationToken authentication = convert(jwt(Map.of(
                JwtClaimNames.RESOURCE_ACCESS, Map.of(CLIENT_ID, Map.of(JwtClaimNames.ROLES, List.of("maintenance-read", "maintenance write")))
        )));

        assertTrue(authorities(authentication).containsAll(Set.of(
                "ROLE_MAINTENANCE_READ", "ROLE_CLIENT_MAINTENANCE_READ",
                "ROLE_MAINTENANCE_WRITE", "ROLE_CLIENT_MAINTENANCE_WRITE"
        )));
    }

    /**
     * Si los roles de realm se emitieran también como {@code ROLE_}, crear en Keycloak un rol de
     * realm llamado igual que un permiso bastaría para concederlo: quien administra el realm no es
     * necesariamente quien escribe el código.
     */
    @Test
    void realmRolesNeverProduceThePlainRolePrefixReservedForClientRoles() {
        AbstractAuthenticationToken authentication = convert(jwt(Map.of(
                JwtClaimNames.REALM_ACCESS, Map.of(JwtClaimNames.ROLES, List.of("maintenance-read"))
        )));

        Set<String> authorities = authorities(authentication);
        assertTrue(authorities.contains("ROLE_REALM_MAINTENANCE_READ"));
        assertFalse(authorities.contains("ROLE_MAINTENANCE_READ"));
    }

    @Test
    void clientRolesOfOtherClientsAreIgnored() {
        AbstractAuthenticationToken authentication = convert(jwt(Map.of(
                JwtClaimNames.RESOURCE_ACCESS, Map.of("mto-configuration-api", Map.of(JwtClaimNames.ROLES, List.of("maintenance-write")))
        )));

        assertTrue(authorities(authentication).isEmpty());
    }

    @Test
    void scopesBecomeScopePrefixedAuthorities() {
        AbstractAuthenticationToken authentication = convert(jwt(Map.of(JwtClaimNames.SCOPE, "openid profile")));

        assertTrue(authorities(authentication).containsAll(Set.of("SCOPE_openid", "SCOPE_profile")));
    }

    @Test
    void principalNameFallsBackToSubjectWhenTheConfiguredClaimIsMissing() {
        assertEquals("maintenance.technician", convert(jwt(Map.of(JwtClaimNames.PREFERRED_USERNAME, "maintenance.technician"))).getName());
        assertEquals("subject-1", convert(jwt(Map.of())).getName());
    }

    @Test
    void audienceValidatorRejectsTokensIssuedForAnotherApplication() {
        JwtAudienceValidator audienceValidator = new JwtAudienceValidator(CLIENT_ID);

        assertFalse(audienceValidator.validate(jwt(Map.of(JwtClaimNames.AUDIENCE, List.of(CLIENT_ID)))).hasErrors());

        OAuth2TokenValidatorResult rejected = audienceValidator.validate(jwt(Map.of(JwtClaimNames.AUDIENCE, List.of("mto-configuration-api"))));
        assertTrue(rejected.hasErrors());
    }

    @Test
    void audienceValidatorRefusesToBeBuiltWithoutAnAudience() {
        assertThrows(IllegalArgumentException.class, () -> new JwtAudienceValidator(" "));
    }

    @Test
    void currentUserServiceReadsTheAuthenticatedUserAndIgnoresAnonymousAuthentication() {
        CurrentUserService currentUserService = new CurrentUserService();

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt(Map.of(JwtClaimNames.PREFERRED_USERNAME, "maintenance.technician", JwtClaimNames.EMAIL, "operator@example.com")),
                List.of(new SimpleGrantedAuthority("ROLE_MAINTENANCE_READ")),
                "maintenance.technician"
        ));

        assertEquals("maintenance.technician", currentUserService.getUsername().orElseThrow());
        assertEquals("subject-1", currentUserService.getUserId().orElseThrow());
        assertEquals("operator@example.com", currentUserService.getEmail().orElseThrow());
        assertTrue(currentUserService.hasRole("MAINTENANCE_READ"));
        assertTrue(currentUserService.hasRole("ROLE_MAINTENANCE_READ"));
        assertFalse(currentUserService.hasRole("MAINTENANCE_WRITE"));

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertTrue(currentUserService.getUsername().isEmpty());
        assertTrue(currentUserService.getAuthorities().isEmpty());
    }

    @Test
    void currentUserServiceReportsNoUserWhenTheContextIsEmpty() {
        assertTrue(new CurrentUserService().getUsername().isEmpty());
    }

    /**
     * Un {@code required-audience} vacío apagaría la validación de audiencia en tiempo de petición y
     * sin rastro en el log, y la API pasaría a aceptar cualquier token del realm. Que la aplicación
     * no arranque es lo que impide que eso ocurra sin que nadie lo note.
     */
    @Test
    void securityPropertiesRefuseAudienceValidationWithoutAnAudience() {
        assertTrue(validator.validate(properties(true, "mto-maintenance-api", List.of("http://localhost:4200"))).isEmpty());
        assertTrue(validator.validate(properties(true, " ", List.of("http://localhost:4200"))).stream()
                .anyMatch(violation -> violation.getMessage().contains("required-audience")));
        assertTrue(validator.validate(properties(false, null, List.of("http://localhost:4200"))).isEmpty());
    }

    @Test
    void securityPropertiesRejectAWildcardCorsOrigin() {
        assertTrue(validator.validate(properties(false, null, List.of("*"))).stream()
                .anyMatch(violation -> violation.getMessage().contains("allowed-origins")));
    }

    private static SecurityProperties properties(boolean audienceValidationEnabled, String requiredAudience, List<String> allowedOrigins) {
        return new SecurityProperties(
                CLIENT_ID,
                JwtClaimNames.PREFERRED_USERNAME,
                audienceValidationEnabled,
                requiredAudience,
                false,
                new SecurityProperties.Cors(allowedOrigins, List.of("GET"), List.of("Authorization"), List.of(), false, 3600)
        );
    }

    private static AbstractAuthenticationToken convert(Jwt jwt) {
        return new KeycloakJwtAuthenticationConverter(properties(false, null, List.of("http://localhost:4200"))).convert(jwt);
    }

    private static Set<String> authorities(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("subject-1")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
