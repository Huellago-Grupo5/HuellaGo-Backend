package com.huellago.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration", "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"})
public class HuellaGoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(HuellaGoBackendApplication.class, args);
	}

}

