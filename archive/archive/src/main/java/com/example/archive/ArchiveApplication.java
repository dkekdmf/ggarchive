package com.example.archive;

import com.example.archive.notification.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(NotificationProperties.class)
public class ArchiveApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArchiveApplication.class, args);
	}

}
