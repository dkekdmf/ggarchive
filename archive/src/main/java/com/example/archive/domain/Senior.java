package com.example.archive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사진을 올리는 어르신. 이름만으로 구분한다.
 * 비밀번호를 만들지 않는 대신, 처음 들어온 이름이 그대로 등록된다.
 */
@Entity
@Table(name = "senior")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Senior {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 30, unique = true)
	private String name;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private Senior(String name) {
		this.name = name;
		this.createdAt = LocalDateTime.now();
	}

	public static Senior of(String name) {
		return new Senior(name);
	}

	/** 목록 화면에 보여 줄 이름 */
	public String getDisplayName() {
		return name;
	}
}
