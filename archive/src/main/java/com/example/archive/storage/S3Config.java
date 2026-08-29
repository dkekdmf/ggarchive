package com.example.archive.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 접속 준비. archive.storage.type=s3 일 때만 만들어진다.
 */
@Configuration
@ConditionalOnProperty(name = "archive.storage.type", havingValue = "s3")
public class S3Config {

	@Bean
	public S3Client s3Client(
			@Value("${cloud.aws.credentials.access-key}") String accessKey,
			@Value("${cloud.aws.credentials.secret-key}") String secretKey,
			@Value("${cloud.aws.region.static}") String region) {

		return S3Client.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKey, secretKey)))
				.build();
	}
}
