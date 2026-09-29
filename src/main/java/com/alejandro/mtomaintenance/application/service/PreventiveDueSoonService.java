package com.alejandro.mtomaintenance.application.service;

import com.alejandro.mtomaintenance.application.dto.asset.PreventiveDueSoonReport;

/**
 * The daily look at the preventive calendar: one event, {@code maintenance.preventive.due-soon},
 * with the enabled assets whose preventive is due within the horizon or already overdue.
 *
 * <p>Corre una vez al dia desde {@code PreventiveDueSoonConfiguration}; los tests lo llaman. Es lo
 * unico que este servicio publica sin que nadie haya escrito nada, y por eso es tambien lo unico
 * cuyo {@code operationId} sale de la fecha: dos instancias, o una segunda pasada tras un reinicio,
 * publican el mismo identificador y el consumidor descarta el duplicado.</p>
 */
public interface PreventiveDueSoonService {

    PreventiveDueSoonReport publishDueSoon();
}
