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

	/**
	 * 업체명. 위와 같은 이유로 DB 제약 대신 코드에서 확인한다.
	 */
	@Column(length = 100)
	private String company;

	/**
	 * 사용(행사) 장소.
	 *
	 * <p>DB 에는 NOT NULL 을 걸지 않는다. 이미 쌓인 행에는 채울 값이 없어서
	 * ddl-auto 가 컬럼을 아예 만들지 못하고, 그러면 앱 전체가 멈춘다.
	 * 새로 받을 때 반드시 채우게 하는 일은 PhotoService 에서 한다.
	 */
	@Column(length = 100)
	private String place;

	/** 사용 목적. 위와 같은 이유로 DB 제약 대신 코드에서 확인한다. */
	@Column(length = 200)
	private String purpose;

	/**
	 * 사용 시작일. 달력과 목록의 정렬 기준이라 이름은 eventDate 그대로 둔다.
	 * (사진을 보낸 날과는 다를 수 있다)
	 */
	@Column(nullable = false)
	private LocalDate eventDate;

	/** 사용 종료일. 당일 행사면 비어 있고, 그때는 시작일과 같은 것으로 본다. */
	private LocalDate usageEnd;

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

	private Event(Senior senior, String title, String company, String place, String purpose,
			LocalDate eventDate, LocalDate usageEnd, String content) {
		this.senior = senior;
		this.title = title;
		this.company = company;
		this.place = place;
		this.purpose = purpose;
		this.eventDate = eventDate;
		this.usageEnd = usageEnd;
		this.content = content;
		this.createdAt = LocalDateTime.now();
		this.checked = false;
	}

	public static Event of(Senior senior, String title, String company, String place, String purpose,
			LocalDate eventDate, LocalDate usageEnd, String content) {
		return new Event(senior, title, company, place, purpose, eventDate, usageEnd, content);
	}

	/** 직원이 항목을 고친다. 사진은 바뀌지 않는다. */
	public void update(String title, String content, LocalDate eventDate) {
		this.title = title;
		this.content = (content == null || content.isBlank()) ? null : content;
		this.eventDate = eventDate;
	}

	public void updateCompany(String company) {
		this.company = company;
	}

	public void updatePlace(String place) {
		this.place = place;
	}

	public void updatePurpose(String purpose) {
		this.purpose = purpose;
	}

	public void updateUsageEnd(LocalDate usageEnd) {
		this.usageEnd = usageEnd;
	}

	/** 여러 날에 걸친 행사인지 */
	public boolean isMultiDay() {
		return usageEnd != null && usageEnd.isAfter(eventDate);
	}

	public void removePhoto(Photo photo) {
		photos.remove(photo);
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

	/** 예전에 올라온 행사는 업체명·장소·목적이 비어 있을 수 있다. 화면에서는 이 값을 쓴다. */
	public String getCompanyOrDash() {
		return (company == null || company.isBlank()) ? "-" : company;
	}

	public String getPlaceOrDash() {
		return (place == null || place.isBlank()) ? "-" : place;
	}

	public String getPurposeOrDash() {
		return (purpose == null || purpose.isBlank()) ? "-" : purpose;
	}

	public boolean hasContent() {
		return content != null && !content.isBlank();
	}
}
