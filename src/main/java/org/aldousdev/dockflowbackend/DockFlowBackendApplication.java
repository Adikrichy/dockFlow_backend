package org.aldousdev.dockflowbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class DockFlowBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DockFlowBackendApplication.class, args);
    }

}
