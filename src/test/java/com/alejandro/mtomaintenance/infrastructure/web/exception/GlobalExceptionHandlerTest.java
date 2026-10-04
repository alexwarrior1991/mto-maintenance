package com.alejandro.mtomaintenance.infrastructure.web.exception;

import com.alejandro.mtomaintenance.application.dto.error.ApiErrorResponse;
import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.DuplicateCodeException;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.exception.UnsyncedMaterialsException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.dto.defect.DefectCommentRequest;
import com.alejandro.mtomaintenance.application.dto.error.ValidationError;
import com.alejandro.mtomaintenance.application.dto.team.MaintenanceTeamRequest;
import com.alejandro.mtomaintenance.application.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import com.alejandro.mtomaintenance.infrastructure.persistence.entity.MaintenanceOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundCarriesAggregatePrefixedCodeAndRequestContext() {
        MockHttpServletRequest request = request("GET", "/api/v1/maintenance/orders/" + UUID.randomUUID());
        request.addHeader("X-Correlation-Id", "corr-1");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleNotFound(new NotFoundException("Maintenance order", UUID.randomUUID()), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ORD-404", response.getBody().errorCode());
        assertEquals("corr-1", response.getBody().correlationId());
        assertEquals("GET", response.getBody().method());
    }

    @Test
    void stateConflictsAnswer409WithTheirOwnCodes() {
        MockHttpServletRequest request = request("POST", "/api/v1/maintenance/orders");

        assertEquals("TRN-001", codeOf(handler.handleStateConflict(new InvalidTransitionException("no"), request), HttpStatus.CONFLICT));
        assertEquals("AST-001", codeOf(handler.handleStateConflict(new AssetDisabledException("no"), request), HttpStatus.CONFLICT));
        assertEquals("SHF-001", codeOf(handler.handleStateConflict(new ShiftException("no"), request), HttpStatus.CONFLICT));
        assertEquals("MAT-001", codeOf(handler.handleStateConflict(new MaterialUsageException("no"), request), HttpStatus.CONFLICT));
        assertEquals("AST-409", codeOf(handler.handleDuplicateCode(new DuplicateCodeException("Catenary asset", "SEC-1"), request), HttpStatus.CONFLICT));
    }

    @Test
    void inspectionRulesAnswer422AndStockOutagesAnswer503() {
        MockHttpServletRequest request = request("POST", "/api/v1/maintenance/inspections");

        assertEquals("INS-001", codeOf(handler.handleInspection(new InspectionException("no"), request), HttpStatus.UNPROCESSABLE_CONTENT));
        assertEquals("STK-503", codeOf(handler.handleStockUnavailable(new StockUnavailableException("down"), request), HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals("VAL-001", codeOf(handler.handleValidation(new ValidationException("bad"), request), HttpStatus.BAD_REQUEST));
    }

    @Test
    void aStockRejectionAnswers409WhenStockIsShortAnd422OtherwiseKeepingTheReasonOfStock() {
        MockHttpServletRequest request = request("POST", "/api/v1/maintenance/orders/1/materials/2/sync");

        ResponseEntity<ApiErrorResponse> shortage = handler.handleStockRejected(new StockRejectedException(
                "mto-stock rejected 'reserve 5 of GA70' with 409 STK-001: Insufficient available stock", 409, "STK-001", null), request);
        assertEquals("STK-001", codeOf(shortage, HttpStatus.CONFLICT));
        assertTrue(shortage.getBody().message().contains("Insufficient available stock"));

        assertEquals("STK-422", codeOf(handler.handleStockRejected(new StockRejectedException(
                "mto-stock rejected 'reserve 5 of GA70' with 422 WH-001: Warehouse 'W1' is inactive", 422, "WH-001", null), request),
                HttpStatus.UNPROCESSABLE_CONTENT));
        assertEquals("STK-422", codeOf(handler.handleStockRejected(new StockRejectedException(
                "mto-stock rejected 'read reservation 1' with 400", 400, null, null), request), HttpStatus.UNPROCESSABLE_CONTENT),
                "A rejection whose body could not be read");
        assertEquals("MAT-001", codeOf(handler.handleStateConflict(new UnsyncedMaterialsException("pending lines"), request), HttpStatus.CONFLICT),
                "Completing with lines not synchronized is still MAT-001");
    }

    @Test
    void aStaleVersionAndACrossedWriteAnswer409Con001AndAnEscapedConstraintA409InsteadOf500() {
        MockHttpServletRequest request = request("PATCH", "/api/v1/maintenance/orders/1");

        assertEquals("CON-001", codeOf(handler.handleStaleVersion(new StaleVersionException("Order MO-1 was changed by someone else"), request),
                HttpStatus.CONFLICT));
        assertEquals("CON-001", codeOf(handler.handleOptimisticLock(new ObjectOptimisticLockingFailureException(MaintenanceOrder.class, UUID.randomUUID()),
                request), HttpStatus.CONFLICT));
        ResponseEntity<ApiErrorResponse> integrity = handler.handleDataIntegrity(
                new DataIntegrityViolationException("chk_maintenance_material_usage_over_consumption"), request);
        assertEquals("APP-409", codeOf(integrity, HttpStatus.CONFLICT));
        assertEquals("The change conflicts with the stored data.", integrity.getBody().message(), "The constraint name does not leak");
    }

    @Test
    void requestShapeErrorsAnswer400Or405InsteadOf500() {
        ResponseEntity<ApiErrorResponse> missing = handler.handleMissingParameter(
                new MissingServletRequestParameterException("month", "YearMonth"), request("GET", "/api/v1/maintenance/reports/monthly"));
        assertEquals("REQ-400", codeOf(missing, HttpStatus.BAD_REQUEST));
        assertEquals(List.of(new ValidationError("month", "is required")), missing.getBody().validationErrors());

        ResponseEntity<ApiErrorResponse> sort = handler.handleUnknownSortProperty(
                new PropertyReferenceException("nope", TypeInformation.of(MaintenanceOrder.class), List.of()),
                request("GET", "/api/v1/maintenance/orders"));
        assertEquals("REQ-400", codeOf(sort, HttpStatus.BAD_REQUEST));
        assertEquals(List.of(new ValidationError("sort", "unknown property 'nope'")), sort.getBody().validationErrors());

        ResponseEntity<ApiErrorResponse> method = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "PUT")), request("PATCH", "/api/v1/maintenance/orders/1"));
        assertEquals("REQ-405", codeOf(method, HttpStatus.METHOD_NOT_ALLOWED));
        assertEquals(Set.of(HttpMethod.GET, HttpMethod.PUT), method.getHeaders().getAllow());
    }

    @Test
    void unexpectedExceptionsDoNotLeakDetails() {
        MockHttpServletRequest request = request("GET", "/api/v1/maintenance/orders");

        ResponseEntity<ApiErrorResponse> response = handler.handleException(new IllegalStateException("secret"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("APP-500", response.getBody().errorCode());
        assertEquals("An unexpected error occurred. Please contact support.", response.getBody().message());
    }

    private static String codeOf(ResponseEntity<ApiErrorResponse> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().errorCode();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    @Test
    void genericBusinessExceptionsAnswer422AndUnknownAggregatesFallBackToTheAppPrefix() {
        MockHttpServletRequest request = request("POST", "/api/v1/maintenance/teams");

        assertEquals("BUS-001", codeOf(handler.handleBusiness(new BusinessException("odd"), request), HttpStatus.UNPROCESSABLE_CONTENT));
        assertEquals("APP-404", codeOf(handler.handleNotFound(new NotFoundException("Widget", UUID.randomUUID()), request), HttpStatus.NOT_FOUND));
        assertEquals("TEA-409", codeOf(handler.handleDuplicateCode(new DuplicateCodeException("Maintenance team", "A"), request), HttpStatus.CONFLICT));
        assertEquals("SHF-404", codeOf(handler.handleNotFound(new NotFoundException("Maintenance shift", UUID.randomUUID()), request), HttpStatus.NOT_FOUND));
        assertEquals("TSK-404", codeOf(handler.handleNotFound(new NotFoundException("Maintenance task", UUID.randomUUID()), request), HttpStatus.NOT_FOUND));
        // Un tipo de tarea se busca por codigo y su 404 no lleva agregado: Map.of no admite get(null), y
        // el NPE dentro del manejador acababa en un 500.
        assertEquals("APP-404", codeOf(handler.handleNotFound(new NotFoundException("Maintenance task type 'RG-99' was not found"), request),
                HttpStatus.NOT_FOUND));
    }

    @Test
    void securityFailuresAnswer401And403WithAFixedMessage() {
        MockHttpServletRequest request = request("GET", "/api/v1/maintenance/orders");

        ResponseEntity<ApiErrorResponse> unauthenticated = handler.handleAuthentication(new BadCredentialsException("token expired"), request);
        ResponseEntity<ApiErrorResponse> forbidden = handler.handleAccessDenied(new AccessDeniedException("needs supervise"), request);

        assertEquals("AUTH-401", codeOf(unauthenticated, HttpStatus.UNAUTHORIZED));
        assertEquals("Authentication is required to access this resource.", unauthenticated.getBody().message());
        assertEquals("AUTH-403", codeOf(forbidden, HttpStatus.FORBIDDEN));
        assertEquals("The authenticated user is not allowed to perform this operation.", forbidden.getBody().message(),
                "The cause of the denial is never echoed to the client");
    }

    @Test
    void malformedRequestsAnswer400OrTheirOwnHttpStatusNamingTheOffendingParameter() {
        MockHttpServletRequest request = request("GET", "/api/v1/maintenance/orders/not-a-uuid");
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException("not-a-uuid", UUID.class, "id", null,
                new IllegalArgumentException("Invalid UUID"));

        ResponseEntity<ApiErrorResponse> response = handler.handleMethodArgumentTypeMismatch(mismatch, request);

        assertEquals("REQ-400", codeOf(response, HttpStatus.BAD_REQUEST));
        assertEquals(List.of(new ValidationError("id", "must be a valid UUID")), response.getBody().validationErrors());
        assertEquals("REQ-400", codeOf(handler.handleHttpMessageNotReadable(request), HttpStatus.BAD_REQUEST));
        assertEquals("HTTP-404", codeOf(handler.handleNoResourceFound(request), HttpStatus.NOT_FOUND));
        assertEquals("REQ-415", codeOf(handler.handleUnsupportedMediaType(new HttpMediaTypeNotSupportedException("text/plain"), request),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        assertEquals("/api/v1/maintenance/orders/not-a-uuid", response.getBody().path());
    }

    @Test
    void constraintViolationsAreListedSortedByPropertyPath() {
        MockHttpServletRequest request = request("POST", "/api/v1/maintenance/teams");
        var violations = Validation.buildDefaultValidatorFactory().getValidator()
                .validate(new MaintenanceTeamRequest(" ", "x".repeat(121), null, null, null, null));

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(new ConstraintViolationException(violations), request);

        assertEquals("REQ-VALIDATION", codeOf(response, HttpStatus.BAD_REQUEST));
        assertEquals(List.of("code", "name"), response.getBody().validationErrors().stream().map(ValidationError::field).toList());
        assertTrue(Validation.buildDefaultValidatorFactory().getValidator().validate(new DefectCommentRequest("ok")).isEmpty());
    }
}
