package com.example.archive.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalPhotoStorage implements PhotoStorage {

	private static final Logger log = LoggerFactory.getLogger(LocalPhotoStorage.class);
	private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final Path root;

	public LocalPhotoStorage(@Value("${archive.storage.location}") String location) {
		this.root = Paths.get(location).toAbsolutePath().normalize();
		try {
			Files.createDirectories(this.root);
		} catch (IOException e) {
			throw new IllegalStateException("사진 저장 폴더를 만들 수 없습니다: " + this.root, e);
		}
	}

	@Override
	public String store(MultipartFile file) {
		String storageKey = LocalDate.now().format(DATE_DIR) + "/" + UUID.randomUUID() + extensionOf(file);
		Path target = resolve(storageKey);
		try {
			Files.createDirectories(target.getParent());
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new IllegalStateException("사진을 저장하지 못했습니다.", e);
		}
		return storageKey;
	}

	@Override
	public Resource load(String storageKey) {
		try {
			Resource resource = new UrlResource(resolve(storageKey).toUri());
			if (!resource.exists() || !resource.isReadable()) {
				throw new IllegalArgumentException("사진 파일을 찾을 수 없습니다: " + storageKey);
			}
			return resource;
		} catch (IOException e) {
			throw new IllegalArgumentException("사진 파일을 읽을 수 없습니다: " + storageKey, e);
		}
	}

	@Override
	public void delete(String storageKey) {
		try {
			Files.deleteIfExists(resolve(storageKey));
		} catch (IOException e) {
			// 파일이 남아도 화면에는 영향이 없다. 지워지지 않았다는 사실만 알리고 넘어간다.
			log.warn("사진 파일을 지우지 못했습니다: {}", storageKey, e);
		}
	}

	/** 저장 폴더 밖으로 벗어나는 경로(../ 등)를 막는다. */
	private Path resolve(String storageKey) {
		Path target = root.resolve(storageKey).normalize();
		if (!target.startsWith(root)) {
			throw new IllegalArgumentException("잘못된 경로입니다: " + storageKey);
		}
		return target;
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
