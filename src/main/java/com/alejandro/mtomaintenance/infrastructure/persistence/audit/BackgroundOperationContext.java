package com.alejandro.mtomaintenance.infrastructure.persistence.audit;

import java.util.UUID;

/**
 * Lo que una operacion de fondo sabe de la peticion que la pidio, para el hilo virtual que la ejecuta.
 *
 * <p>La peticion ya no existe cuando el trabajo sigue en otro hilo (Tomcat recicla el objeto), asi que
 * no se puede propagar {@code RequestContextHolder}: se copia aqui lo que la auditoria y el sobre de los
 * eventos leen de ella, al apuntar la operacion, y el hilo que la ejecuta lo fija. Asi una revision de
 * Envers escrita en segundo plano sigue diciendo {@code HTTP} con su URI y su correlacion, y el evento
 * que publica lleva el mismo {@code correlationId} que la peticion.</p>
 *
 * <p>Como {@link MessagingAuditContext}, se limpia siempre en un {@code finally}.</p>
 */
public final class BackgroundOperationContext {

    /**
     * @param operationId   la operacion de fondo
     * @param correlationId el {@code X-Correlation-Id} de la peticion que la pidio
     * @param requestMethod y {@code requestUri}, {@code ipAddress}, {@code userAgent}: los de esa peticion
     */
    public record Context(UUID operationId, String correlationId, String requestMethod, String requestUri,
                          String ipAddress, String userAgent) {
    }

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private BackgroundOperationContext() {
    }

    public static void set(Context context) {
        CURRENT.set(context);
    }

    public static Context current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
