package com.alejandro.mtomaintenance.infrastructure.web.exception;

import com.alejandro.mtomaintenance.application.dto.operation.OperationError;
import com.alejandro.mtomaintenance.application.exception.AssetDisabledException;
import com.alejandro.mtomaintenance.application.exception.BusinessException;
import com.alejandro.mtomaintenance.application.exception.DuplicateCodeException;
import com.alejandro.mtomaintenance.application.exception.InspectionException;
import com.alejandro.mtomaintenance.application.exception.InvalidTransitionException;
import com.alejandro.mtomaintenance.application.exception.MaterialUsageException;
import com.alejandro.mtomaintenance.application.exception.NotFoundException;
import com.alejandro.mtomaintenance.application.exception.ShiftException;
import com.alejandro.mtomaintenance.application.exception.StaleVersionException;
import com.alejandro.mtomaintenance.application.exception.StockRejectedException;
import com.alejandro.mtomaintenance.application.exception.StockUnavailableException;
import com.alejandro.mtomaintenance.application.exception.SynchronizedTeamException;
import com.alejandro.mtomaintenance.application.exception.TooManyOperationsException;
import com.alejandro.mtomaintenance.application.exception.ValidationException;
import com.alejandro.mtomaintenance.application.service.OperationErrorTranslator;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Lo que habria respondido {@link GlobalExceptionHandler} a la llamada sincrona, para la operacion de
 * fondo que fallo: el mismo estado y el mismo codigo, porque los dos leen {@link BusinessErrorCodeResolver}.
 * Un manejador nuevo en {@link GlobalExceptionHandler} con otro estado se refleja aqui.
 */
@Component
class ApiOperationErrorTranslator implements OperationErrorTranslator {

    @Override
    public OperationError translate(Throwable failure) {
        return switch (failure) {
            case BusinessException business -> new OperationError(status(business).value(),
                    BusinessErrorCodeResolver.resolve(business), business.getMessage());
            case ObjectOptimisticLockingFailureException ignored -> conflict();
            case OptimisticLockException ignored -> conflict();
            case DataIntegrityViolationException ignored ->
                    new OperationError(HttpStatus.CONFLICT.value(), "APP-409", "The change conflicts with the stored data.");
            case AccessDeniedException ignored ->
                    new OperationError(HttpStatus.FORBIDDEN.value(), "AUTH-403", "Access denied.");
            default -> new OperationError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "APP-500",
                    "An unexpected error occurred. Please contact support.");
        };
    }

    private static HttpStatus status(BusinessException exception) {
        return switch (exception) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case DuplicateCodeException ignored -> HttpStatus.CONFLICT;
            case InvalidTransitionException ignored -> HttpStatus.CONFLICT;
            case AssetDisabledException ignored -> HttpStatus.CONFLICT;
            case ShiftException ignored -> HttpStatus.CONFLICT;
            case MaterialUsageException ignored -> HttpStatus.CONFLICT;
            case SynchronizedTeamException ignored -> HttpStatus.CONFLICT;
            case StaleVersionException ignored -> HttpStatus.CONFLICT;
            case InspectionException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
            case StockUnavailableException ignored -> HttpStatus.SERVICE_UNAVAILABLE;
            case StockRejectedException rejected -> rejected.isInsufficientStock() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_CONTENT;
            case TooManyOperationsException ignored -> HttpStatus.TOO_MANY_REQUESTS;
            case ValidationException ignored -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    private static OperationError conflict() {
        return new OperationError(HttpStatus.CONFLICT.value(), "CON-001", "Someone else changed it at the same time; read it again.");
    }
}
