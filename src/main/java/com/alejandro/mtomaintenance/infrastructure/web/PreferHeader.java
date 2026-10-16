package com.alejandro.mtomaintenance.infrastructure.web;

import java.time.Duration;
import java.util.Locale;

/**
 * La cabecera {@code Prefer} de la RFC 7240, en lo que lee este servicio: {@code respond-async} (el
 * cliente acepta un {@code 202 Accepted} si la respuesta tarda) y {@code wait=N} (cuantos segundos
 * esta dispuesto a esperar antes). Lo demas se ignora, como pide la RFC.
 *
 * @param respondAsync si el cliente acepta el 202
 * @param waitFor      lo que pide esperar; {@code null} si no lo dice
 */
public record PreferHeader(boolean respondAsync, Duration waitFor) {

    public static final String NAME = "Prefer";
    public static final String PREFERENCE_APPLIED = "Preference-Applied";
    public static final String RESPOND_ASYNC = "respond-async";

    private static final PreferHeader NONE = new PreferHeader(false, null);

    public static PreferHeader parse(String header) {
        if (header == null || header.isBlank()) {
            return NONE;
        }
        boolean respondAsync = false;
        Duration wait = null;
        for (String raw : header.split(",")) {
            String preference = raw.trim().toLowerCase(Locale.ROOT);
            int semicolon = preference.indexOf(';');
            if (semicolon >= 0) {
                preference = preference.substring(0, semicolon).trim();
            }
            if (preference.equals(RESPOND_ASYNC)) {
                respondAsync = true;
            } else if (preference.startsWith("wait=")) {
                wait = seconds(preference.substring("wait=".length()));
            }
        }
        return new PreferHeader(respondAsync, wait);
    }

    private static Duration seconds(String value) {
        try {
            long seconds = Long.parseLong(value.replace("\"", "").trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
