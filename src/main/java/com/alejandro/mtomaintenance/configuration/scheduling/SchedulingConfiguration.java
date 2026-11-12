package com.alejandro.mtomaintenance.configuration.scheduling;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.boot.thread.Threading;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * El planificador de los {@code @Scheduled}: un pool de {@code spring.task.scheduling.pool.size} (4)
 * hilos, virtuales con {@code spring.threads.virtual.enabled}, para que ninguna pasada espere a otra.
 * El mismo que el de {@code mto-notification}.
 *
 * <p>El latido, el abandono y la purga de las operaciones de fondo, el relay del outbox, sus metricas y
 * su purga, el reintento de stock y la purga del inbox son {@code fixedDelay}. Con los hilos virtuales,
 * Boot planifica con un {@code SimpleAsyncTaskScheduler}, que corre todas las tareas {@code fixedDelay}
 * en su unico hilo de planificacion; sin ellos, con un {@code ThreadPoolTaskScheduler} de un hilo si
 * nadie fija el tamano. Asi, el relay esperando los confirms de un broker lento, o el reintento de
 * stock esperando a mto-stock linea a linea, paraban el latido: pasado {@code app.operations.stale-after},
 * otra replica daba por abandonadas operaciones que seguian trabajando. Aqui cada tarea sigue sin
 * solaparse consigo misma (el siguiente {@code fixedDelay} se cuenta desde que termina la pasada
 * anterior), pero las demas no la esperan.</p>
 *
 * <p>El planificador no es un bean a proposito: un {@code ThreadPoolTaskScheduler} es tambien un
 * {@code Executor}, y lo que pide un executor por tipo ({@code @Async}, el dispatch asincrono de MVC)
 * tiene que seguir encontrando solo el de {@code AsyncConfiguration}. Boot sigue creando su
 * {@code taskScheduler}, en el que ya no se planifica nada. Este se cierra con el contexto.</p>
 *
 * <p>No toca el hilo {@code outbox-dispatch} de {@code OutboxConfiguration}, que no es un
 * {@code @Scheduled}: sigue siendo un unico hilo de plataforma, el que funde los despertares del
 * despacho inmediato.</p>
 */
@Configuration(proxyBeanMethods = false)
public class SchedulingConfiguration implements SchedulingConfigurer, DisposableBean {

    private final ThreadPoolTaskScheduler scheduler;

    public SchedulingConfiguration(ThreadPoolTaskSchedulerBuilder builder, Environment environment) {
        ThreadPoolTaskScheduler built = builder.build();
        if (Threading.VIRTUAL.isActive(environment)) {
            built.setThreadFactory(Thread.ofVirtual().name(built.getThreadNamePrefix(), 0).factory());
        }
        built.initialize();
        this.scheduler = built;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(scheduler);
    }

    @Override
    public void destroy() {
        scheduler.destroy();
    }
}
