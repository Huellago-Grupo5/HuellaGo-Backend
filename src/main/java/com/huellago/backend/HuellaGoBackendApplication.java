package com.huellago.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HuellaGoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(HuellaGoBackendApplication.class, args);
	}

}
