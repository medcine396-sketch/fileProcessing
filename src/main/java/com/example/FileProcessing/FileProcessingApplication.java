package com.example.FileProcessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FileProcessingApplication {

	public static void main(String[] args) {
		SpringApplication.run(FileProcessingApplication.class, args);
	}

}
