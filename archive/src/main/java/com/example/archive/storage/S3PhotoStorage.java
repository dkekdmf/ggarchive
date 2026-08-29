package com.example.archive.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 사진을 S3 에 저장한다. archive.storage.type=s3 일 때 쓰인다.
 *
 * <p>저장하는 키 모양은 로컬과 똑같이 날짜 폴더 + 임의 이름이다.
 * 덕분에 DB 에 담긴 기존 경로를 그대로 쓸 수 있고, 나중에 서로 옮기기도 쉽다.
 */
@Component
@ConditionalOnProperty(name = "archive.storage.type", havingValue = "s3")
public class S3PhotoStorage implements PhotoStorage {

	private static final Logger log = LoggerFactory.getLogger(S3PhotoStorage.class);
	private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final S3Client s3;
	private final String bucket;

	public S3PhotoStorage(S3Client s3, @Value("${cloud.aws.s3.bucket}") String bucket) {
		this.s3 = s3;
		this.bucket = bucket;
		log.info("사진 저장소: S3 (버킷 {})", bucket);
	}

	@Override
	public String store(MultipartFile file) {
		String storageKey = LocalDate.now().format(DATE_DIR) + "/" + UUID.randomUUID() + extensionOf(file);

		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket)
				.key(storageKey)
				.contentType(file.getContentType())
				.contentLength(file.getSize())
				.build();

		try (InputStream in = file.getInputStream()) {
			s3.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
		} catch (IOException | S3Exception e) {
			throw new IllegalStateException("사진을 S3 에 올리지 못했습니다.", e);
		}
		return storageKey;
	}

	@Override
	public Resource load(String storageKey) {
		try {
			// 응답 스트림을 그대로 넘긴다. 파일을 통째로 메모리에 올리지 않는다.
			return new InputStreamResource(s3.getObject(GetObjectRequest.builder()
					.bucket(bucket)
					.key(storageKey)
					.build()));
		} catch (NoSuchKeyException e) {
			throw new IllegalArgumentException("사진 파일을 찾을 수 없습니다: " + storageKey, e);
		} catch (S3Exception e) {
			throw new IllegalStateException("사진 파일을 읽지 못했습니다: " + storageKey, e);
		}
	}

	@Override
	public void delete(String storageKey) {
		try {
			s3.deleteObject(DeleteObjectRequest.builder()
					.bucket(bucket)
					.key(storageKey)
					.build());
		} catch (S3Exception e) {
			// 파일이 남아도 화면에는 영향이 없다. 지워지지 않았다는 사실만 알리고 넘어간다.
			log.warn("S3 에서 사진을 지우지 못했습니다: {}", storageKey, e);
		}
	}

	private String extensionOf(MultipartFile file) {
		String contentType = file.getContentType();
		if (contentType == null) {
			return ".jpg";
		}
		return switch (contentType) {
			case "image/png" -> ".png";
			case "image/gif" -> ".gif";
			case "image/webp" -> ".webp";
			case "image/heic", "image/heif" -> ".heic";
			default -> ".jpg";
		};
	}
}
