package com.example.archive.service;

import com.example.archive.domain.Event;
import com.example.archive.domain.Photo;
import com.example.archive.domain.Senior;
import com.example.archive.notification.PhotoUploadedEvent;
import com.example.archive.repository.EventRepository;
import com.example.archive.repository.PhotoRepository;
import com.example.archive.repository.SeniorRepository;
import com.example.archive.storage.PhotoStorage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class
PhotoService {

	private final EventRepository eventRepository;
	private final PhotoRepository photoRepository;
	private final SeniorRepository seniorRepository;
	private final PhotoStorage photoStorage;
	private final ApplicationEventPublisher eventPublisher;
	private final int maxPhotosPerUpload;

	public PhotoService(EventRepository eventRepository, PhotoRepository photoRepository,
			SeniorRepository seniorRepository, PhotoStorage photoStorage,
			ApplicationEventPublisher eventPublisher,
			@Value("${archive.upload.max-count:10}") int maxPhotosPerUpload) {
		this.eventRepository = eventRepository;
		this.photoRepository = photoRepository;
		this.seniorRepository = seniorRepository;
		this.photoStorage = photoStorage;
		this.eventPublisher = eventPublisher;
		this.maxPhotosPerUpload = maxPhotosPerUpload;
	}

	/**
	 * 모든 행사가 매달리는 자리표.
	 *
	 * <p>이름은 더 이상 받지 않는다. 다만 event 테이블의 senior_id 는 NOT NULL 이고
	 * 이미 행이 쌓여 있어서 컬럼을 없앨 수가 없다. 그래서 이 하나에 전부 붙여 둔다.
	 * 화면 어디에도 나오지 않는다.
	 */
	static final String ANONYMOUS = "이름 없음";

	public int getMaxPhotosPerUpload() {
		return maxPhotosPerUpload;
	}

	/** 이름으로 어르신을 찾고, 처음 오신 분이면 그대로 등록한다. */
	@Transactional
	public Senior identify(String name) {
		String trimmed = name.trim();
		return seniorRepository.findByName(trimmed)
				.orElseGet(() -> seniorRepository.save(Senior.of(trimmed)));
	}

	/**
	 * 행사 하나를 사진들과 함께 저장한다.
	 *
	 * <p>한 장이라도 실패하면 전체를 되돌린다. 어르신께 "3장 중 2장만 갔습니다" 같은 상황을
	 * 설명하고 다시 고르게 하는 것보다, 통째로 다시 보내시게 하는 편이 훨씬 덜 헷갈린다.
	 *
	 * <p>알림 메일은 장수와 상관없이 행사당 한 통만 나간다.
	 */
	@Transactional
	public Event submit(String title, String company, String place, String purpose,
			LocalDate eventDate, LocalDate usageEnd, String content,
			List<MultipartFile> files) {

		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("행사 이름을 적어 주세요.");
		}
		if (company == null || company.isBlank()) {
			throw new IllegalArgumentException("업체명을 적어 주세요.");
		}
		if (place == null || place.isBlank()) {
			throw new IllegalArgumentException("사용 장소를 적어 주세요.");
		}
		if (purpose == null || purpose.isBlank()) {
			throw new IllegalArgumentException("사용 목적을 적어 주세요.");
		}
		if (eventDate == null) {
			throw new IllegalArgumentException("사용 시작일을 골라 주세요.");
		}
		if (usageEnd != null && usageEnd.isBefore(eventDate)) {
			throw new IllegalArgumentException("사용 종료일이 시작일보다 앞설 수 없습니다.");
		}

		List<MultipartFile> actual = files == null ? List.of()
				:

                files.stream().filter(f -> f != null && !f.isEmpty()).toList();

		// 사진이 없어도 저장한다. 일정만 먼저 적어 두고 나중에 사진을 넣는 경우가 있다.
		if (actual.size() > maxPhotosPerUpload) {
			throw new IllegalArgumentException(
					"한 번에 %d장까지 보낼 수 있습니다.".formatted(maxPhotosPerUpload));
		}
		for (MultipartFile file : actual) {
			String contentType = file.getContentType();
			if (contentType == null || !contentType.startsWith("image/")) {
				throw new IllegalArgumentException("사진 파일만 보낼 수 있습니다.");
			}
		}

		Senior senior = identify(ANONYMOUS);

		Event event = Event.of(senior, title.trim(), company.trim(), place.trim(), purpose.trim(),
				eventDate, usageEnd,
				(content == null || content.isBlank()) ? null : content.trim());

		for (MultipartFile file : actual) {
			String storageKey = photoStorage.store(file);
			String originalName = file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename();
			event.addPhoto(Photo.of(storageKey, originalName, file.getContentType(), file.getSize()));
		}

		Event saved = eventRepository.save(event);

		// 저장이 확정된 뒤에 담당 직원에게 메일이 나간다 (PhotoNotifier 참고).
		// 사진 10장을 보내도 메일은 한 통이다.
		eventPublisher.publishEvent(new PhotoUploadedEvent(
				saved.getId(), saved.getCompanyOrDash(),
				saved.getTitle(), saved.getEventDate(), saved.getCreatedAt(), saved.getPhotoCount()));

		return saved;
	}

	public Page<Event> search(String keyword, LocalDate from, LocalDate to, boolean onlyUnchecked,
			int page, int size) {
		String trimmed = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		Page<Event> events = eventRepository.search(trimmed, from, to, onlyUnchecked, PageRequest.of(page, size));

		// 화면에서 사진을 바로 그려야 하는데 open-in-view 를 꺼 두었으므로
		// 아직 트랜잭션이 살아 있는 여기에서 미리 읽어 둔다 (@BatchSize 로 한 번에 가져온다).
		events.getContent().forEach(event -> event.getPhotos().size());
		return events;
	}

	/**
	 * 달력에 그릴 한 달치 행사를 날짜별로 묶어 돌려준다.
	 * 대표 사진을 쓰므로 트랜잭션이 살아 있는 여기에서 사진을 미리 읽어 둔다.
	 */
	public Map<LocalDate, List<Event>> eventsByDay(YearMonth month) {
		List<Event> events = eventRepository.findBetween(month.atDay(1), month.atEndOfMonth());
		events.forEach(event -> event.getPhotos().size());

		Map<LocalDate, List<Event>> byDay = new HashMap<>();
		for (Event event : events) {
			byDay.computeIfAbsent(event.getEventDate(), day -> new ArrayList<>()).add(event);
		}
		return byDay;
	}

	public Event getEvent(Long id) {
		return eventRepository.findWithPhotos(id)
				.orElseThrow(() -> new IllegalArgumentException("행사를 찾을 수 없습니다: " + id));
	}

	public Photo getPhoto(Long id) {
		return photoRepository.findWithEvent(id)
				.orElseThrow(() -> new IllegalArgumentException("사진을 찾을 수 없습니다: " + id));
	}

	public Resource loadFile(Photo photo) {
		return photoStorage.load(photo.getStorageKey());
	}

	/** 직원이 행사 정보를 고친다. 사진은 그대로 둔다. */
	@Transactional
	public Event updateEvent(Long eventId, String title, String content, LocalDate eventDate) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("행사 제목을 적어 주세요.");
		}
		if (eventDate == null) {
			throw new IllegalArgumentException("행사 날짜를 골라 주세요.");
		}

		Event event = eventRepository.findById(eventId)
				.orElseThrow(() -> new IllegalArgumentException("행사를 찾을 수 없습니다: " + eventId));
		event.update(title.trim(), content == null ? null : content.trim(), eventDate);
		return event;
	}

	/**
	 * 항목 하나만 고친다. 상세 화면에서 제목이면 제목, 날짜면 날짜만 바로 저장할 때 쓴다.
	 *
	 * @param field title | eventDate | content 중 하나
	 */
	@Transactional
	public Event updateField(Long eventId, String field, String value) {
		Event event = eventRepository.findById(eventId)
				.orElseThrow(() -> new IllegalArgumentException("행사를 찾을 수 없습니다: " + eventId));

		String trimmed = value == null ? "" : value.trim();

		switch (field == null ? "" : field) {
			case "title" -> {
				if (trimmed.isBlank()) {
					throw new IllegalArgumentException("행사 이름을 적어 주세요.");
				}
				if (trimmed.length() > 100) {
					throw new IllegalArgumentException("행사 이름이 너무 깁니다. 100자까지 됩니다.");
				}
				event.update(trimmed, event.getContent(), event.getEventDate());
			}
			case "eventDate" -> {
				// 앞으로 있을 행사도 적을 수 있으므로 날짜에 제한을 두지 않는다
				event.update(event.getTitle(), event.getContent(), parseDate(trimmed));
			}
			case "company" -> {
				if (trimmed.isBlank()) {
					throw new IllegalArgumentException("업체명을 적어 주세요.");
				}
				event.updateCompany(trimmed);
			}
			case "place" -> {
				if (trimmed.isBlank()) {
					throw new IllegalArgumentException("사용 장소를 적어 주세요.");
				}
				event.updatePlace(trimmed);
			}
			case "purpose" -> {
				if (trimmed.isBlank()) {
					throw new IllegalArgumentException("사용 목적을 적어 주세요.");
				}
				event.updatePurpose(trimmed);
			}
			case "usageEnd" -> {
				// 비우면 당일 행사로 되돌린다
				LocalDate end = trimmed.isBlank() ? null : parseDate(trimmed);
				if (end != null && end.isBefore(event.getEventDate())) {
					throw new IllegalArgumentException("사용 종료일이 시작일보다 앞설 수 없습니다.");
				}
				event.updateUsageEnd(end);
			}
			case "content" -> {
				if (trimmed.length() > 2000) {
					throw new IllegalArgumentException("행사 내용이 너무 깁니다. 2000자까지 됩니다.");
				}
				event.update(event.getTitle(), trimmed, event.getEventDate());
			}
			default -> throw new IllegalArgumentException("고칠 수 없는 항목입니다: " + field);
		}
		return event;
	}

	private LocalDate parseDate(String value) {
		try {
			return LocalDate.parse(value);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("행사 날짜를 골라 주세요.");
		}
	}

	/**
	 * 이미 올라온 행사에 사진을 더 넣는다.
	 * 빠뜨린 사진을 나중에 채워 넣거나, 예정으로 먼저 적어 둔 행사에 사진을 붙일 때 쓴다.
	 */
	@Transactional
	public Event addPhotos(Long eventId, List<MultipartFile> files) {
		List<MultipartFile> actual = files == null ? List.of()
				: files.stream().filter(f -> f != null && !f.isEmpty()).toList();

		if (actual.isEmpty()) {
			throw new IllegalArgumentException("사진을 골라 주세요.");
		}
		for (MultipartFile file : actual) {
			String contentType = file.getContentType();
			if (contentType == null || !contentType.startsWith("image/")) {
				throw new IllegalArgumentException("사진 파일만 넣을 수 있습니다.");
			}
		}

		Event event = eventRepository.findWithPhotos(eventId)
				.orElseThrow(() -> new IllegalArgumentException("행사를 찾을 수 없습니다: " + eventId));

		int room = maxPhotosPerUpload - event.getPhotoCount();
		if (actual.size() > room) {
			throw new IllegalArgumentException(
					"한 행사에 사진은 %d장까지 넣을 수 있습니다. 지금 %d장이라 %d장까지 더 넣을 수 있어요."
							.formatted(maxPhotosPerUpload, event.getPhotoCount(), Math.max(room, 0)));
		}

		for (MultipartFile file : actual) {
			String storageKey = photoStorage.store(file);
			String originalName = file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename();
			event.addPhoto(Photo.of(storageKey, originalName, file.getContentType(), file.getSize()));
		}
		return event;
	}

	/** 행사를 사진까지 통째로 지운다. */
	@Transactional
	public String deleteEvent(Long eventId) {
		Event event = eventRepository.findWithPhotos(eventId)
				.orElseThrow(() -> new IllegalArgumentException("행사를 찾을 수 없습니다: " + eventId));

		List<String> keys = event.getPhotos().stream().map(Photo::getStorageKey).toList();
		String title = event.getTitle();

		eventRepository.delete(event);
		eventPublisher.publishEvent(new DeletedPhotoFilesEvent(keys));
		return title;
	}

	/** 행사에서 사진 한 장만 뺀다. */
	@Transactional
	public Long deletePhoto(Long photoId) {
		Photo photo = photoRepository.findById(photoId)
				.orElseThrow(() -> new IllegalArgumentException("사진을 찾을 수 없습니다: " + photoId));

		Event event = photo.getEvent();
		String key = photo.getStorageKey();

		event.removePhoto(photo);
		photoRepository.delete(photo);
		eventPublisher.publishEvent(new DeletedPhotoFilesEvent(List.of(key)));
		return event.getId();
	}

	@Transactional
	public void toggleChecked(Long eventId) {
		eventRepository.findById(eventId)
				.orElseThrow(() -> new IllegalArgumentException("행사를 찾을 수 없습니다: " + eventId))
				.toggleChecked();
	}

	public long countUnchecked() {
		return eventRepository.countByCheckedFalse();
	}

	public long countToday() {
		return eventRepository.countByCreatedAtGreaterThanEqual(LocalDate.now().atStartOfDay());
	}
}
