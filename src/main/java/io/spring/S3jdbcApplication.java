package io.spring;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.task.configuration.EnableTask;

@EnableBatchProcessing
@EnableTask
@SpringBootApplication
public class S3jdbcApplication {

	public static void main(String[] args) {
		SpringApplication.run(S3jdbcApplication.class, args);
	}
}
