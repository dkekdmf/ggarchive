package com.example.archive.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * 어르신이 한 번에 보낸 행사 기록. 제목·내용·날짜와 사진 여러 장이 하나로 묶인다.
 *
 * <p>표 이름을 event_post 로 둔 것은 MySQL 에서 event 가 예약어이기 때문이다.
 */
@Entity
@Table(name = "event_post", indexes = @Index(name = "idx_event_created_at", columnList = "createdAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "senior_id", nullable = false)
	private Senior senior;

	@Column(nullable = false, length = 100)
	private String title;

	/** 행사 내용. 적지 않으셔도 된다. */
	@Column(length = 2000)
	private String content;

	/** 행사가 열린 날 (사진을 보낸 날과 다를 수 있다) */
	@Column(nullable = false)
	private LocalDate eventDate;

	/** 실제로 보낸 시각 */
	@Column(nullable = false)
	private LocalDateTime createdAt;

	/** 직원이 확인 처리했는지 여부. 사진 한 장이 아니라 행사 단위로 관리한다. */
	@Column(nullable = false)
	private boolean checked;

	private LocalDateTime checkedAt;

	/** 목록 화면에서 행사 12건의 사진을 각각 따로 조회하지 않도록 묶어서 읽는다. */
	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id asc")
	@BatchSize(size = 50)
	private List<Photo> photos = new ArrayList<>();

	private Event(Senior senior, String title, String content, LocalDate eventDate) {
		this.senior = senior;
		this.title = title;
		this.content = content;
		this.eventDate = eventDate;
		this.createdAt = LocalDateTime.now();
		this.checked = false;
	}

	public static Event of(Senior senior, String title, String content, LocalDate eventDate) {
		return new Event(senior, title, content, eventDate);
	}

	public void addPhoto(Photo photo) {
		photos.add(photo);
		photo.assignTo(this);
	}

	public void toggleChecked() {
		this.checked = !this.checked;
		this.checkedAt = this.checked ? LocalDateTime.now() : null;
	}

	public int getPhotoCount() {
		return photos.size();
	}

	public boolean hasContent() {
		return content != null && !content.isBlank();
	}
}
