package com.alejandro.mtomaintenance.application.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Lineas de una orden que mto-stock no contesto en el plazo de su transicion: quedaron FAILED, con lo
 * que tuvieran en duda, y {@link DeferredStockSyncListener} las reintenta en cuanto la transaccion
 * confirma.
 *
 * @param orderId  la orden
 * @param lineIds  las lineas cortadas por el plazo
 * @param inFlight las conversaciones cortadas: la llamada que tuvieran en el aire termina antes de
 *                 reintentar, para que el reintento no se cruce con ella
 */
record StockLinesDeferred(UUID orderId, List<UUID> lineIds, List<CompletableFuture<?>> inFlight) {
}
