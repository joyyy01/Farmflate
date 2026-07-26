package com.example.aiworkspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AiWorkspaceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiWorkspaceApplication.class, args);
	}

}
