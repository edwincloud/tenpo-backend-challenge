package com.tenpo.challenge.config;

import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Scheduler con virtual threads, para guardar el historial sin bloquear nada.
 *
 * Nota: acá con WebFlux ya todo es no bloqueante (R2DBC, WebClient), así que los virtual
 * threads no cambian el rendimiento en la práctica. Los dejo puestos más que nada para
 * mostrar que se sabe usar la herramienta, y porque si el día de mañana cambian el guardado
 * del historial por algo que sí bloquea (un JDBC de toda la vida, por ejemplo), esto sigue
 * funcionando bien sin que se acaben los hilos.
 */
@Configuration
public class VirtualThreadSchedulerConfig {

    @Bean(destroyMethod = "dispose")
    public Scheduler historyWriteScheduler() {
        return Schedulers.fromExecutorService(Executors.newVirtualThreadPerTaskExecutor(), "history-writer-vt");
    }
}
