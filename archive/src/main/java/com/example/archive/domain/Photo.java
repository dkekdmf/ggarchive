package com.example.archive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사에 딸린 사진 1장. 파일 자체는 디스크에 있고, 여기에는 위치와 정보만 저장한다.
 */
@Entity
@Table(name = "photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Photo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	/** 저장소 안에서의 상대 경로. 예: 2026/08/14/3f2a....jpg */
	@Column(nullable = false, length = 300)
	private String storageKey;

	@Column(nullable = false, length = 255)
	private String originalName;

	@Column(nullable = false, length = 100)
	private String contentType;

	@Column(nullable = false)
	private long sizeBytes;

	@Column(nullable = false)
	private LocalDateTime uploadedAt;

	private Photo(String storageKey, String originalName, String contentType, long sizeBytes) {
		this.storageKey = storageKey;
		this.originalName = originalName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.uploadedAt = LocalDateTime.now();
	}

	public static Photo of(String storageKey, String originalName, String contentType, long sizeBytes) {
		return new Photo(storageKey, originalName, contentType, sizeBytes);
	}

	/** Event.addPhoto 에서만 호출한다. */
	void assignTo(Event event) {
		this.event = event;
	}

}
