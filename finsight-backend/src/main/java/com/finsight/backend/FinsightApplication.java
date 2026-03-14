package com.finsight.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class FinsightApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinsightApplication.class, args);
	}

}
