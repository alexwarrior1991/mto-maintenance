package com.alejandro.mtomaintenance.configuration.async;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executors;

/**
 * El executor de lo que corre en segundo plano: un hilo virtual por tarea, con el {@code SecurityContext}
 * y el MDC de quien la encola. Copia del de {@code mto-configuration}, como en {@code mto-resources}.
 *
 * <p>{@code spring.threads.virtual.enabled} pone en hilos virtuales Tomcat, {@code @Scheduled} y los
 * listeners de RabbitMQ, pero un executor propio no lo lee: hay que pedirlo. Y un hilo virtual recien
 * creado arranca sin autenticacion ni MDC, que son {@code ThreadLocal}: sin el envoltorio, una operacion
 * de fondo escribiria como {@code system}, su revision de Envers no tendria usuario y sus eventos no
 * llevarian el {@code correlationId} de la peticion.</p>
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(OperationsProperties.class)
public class AsyncConfiguration {

    /**
     * Mismo nombre que el de Boot a proposito: al declararlo, su autoconfiguracion se retira y queda un
     * unico executor, el mismo que usa el dispatch asincrono de MVC.
     */
    public static final String TASK_EXECUTOR = "applicationTaskExecutor";

    @Bean(TASK_EXECUTOR)
    public AsyncTaskExecutor applicationTaskExecutor() {
        TaskExecutorAdapter adapter = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
        adapter.setTaskDecorator(new MdcTaskDecorator());
        return new DelegatingSecurityContextAsyncTaskExecutor(adapter);
    }
}
