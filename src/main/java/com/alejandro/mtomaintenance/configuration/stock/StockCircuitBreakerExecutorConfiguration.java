package com.alejandro.mtomaintenance.configuration.stock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.thread.Threading;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * El hilo en el que el circuito {@code stock} hace cada llamada a mto-stock.
 *
 * <p>Con el {@code TimeLimiter}, Spring Cloud CircuitBreaker no llama en el hilo de quien pide: manda
 * la llamada a su executor y espera el resultado con el tiempo maximo. Ese executor es por defecto un
 * {@code Executors.newCachedThreadPool()} de hilos de plataforma, y {@code spring.threads.virtual.enabled}
 * no lo toca: con los hilos virtuales activos, cada llamada a stock seguia esperando en un hilo del
 * sistema operativo. Aqui va a un hilo virtual nuevo, {@code stock-circuit-N} en los logs.</p>
 *
 * <p>Nada mas cambia: el {@code TimeLimiter} interrumpe el hilo virtual igual que a uno de plataforma,
 * y el token de la cuenta de servicio ya se pedia fuera de cualquier peticion HTTP
 * ({@link StockClientConfiguration}). Solo con stock y con los hilos virtuales activos. El executor no
 * es un bean a proposito: un {@code Executor} en el contexto haria que Boot dejara de crear su
 * {@code applicationTaskExecutor}.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.stock", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnThreading(Threading.VIRTUAL)
public class StockCircuitBreakerExecutorConfiguration {

    static final String THREAD_NAME_PREFIX = "stock-circuit-";

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> stockCircuitBreakerExecutor() {
        return factory -> factory.configureExecutorService(virtualThreadExecutor());
    }

    private static ExecutorService virtualThreadExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(THREAD_NAME_PREFIX, 0).factory());
    }
}
