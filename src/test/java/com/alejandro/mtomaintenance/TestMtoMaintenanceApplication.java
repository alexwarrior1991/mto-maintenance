package com.alejandro.mtomaintenance;

import org.springframework.boot.SpringApplication;

public class TestMtoMaintenanceApplication {

    public static void main(String[] args) {
        SpringApplication.from(MtoMaintenanceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
