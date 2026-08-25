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
public class PhotoService {

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
	public Event submit(Long seniorId, String title, String content, LocalDate eventDate,
			List<MultipartFile> files) {

		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("행사 제목을 적어 주세요.");
		}
		if (eventDate == null) {
			throw new IllegalArgumentException("행사 날짜를 골라 주세요.");
		}
		if (eventDate.isAfter(LocalDate.now())) {
			throw new IllegalArgumentException("아직 오지 않은 날짜는 고를 수 없습니다.");
		}

		List<MultipartFile> actual = files == null ? List.of()
				: files.stream().filter(f -> f != null && !f.isEmpty()).toList();

		if (actual.isEmpty()) {
			throw new IllegalArgumentException("사진이 없습니다. 사진을 골라 주세요.");
		}
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

		Senior senior = seniorRepository.findById(seniorId)
				.orElseThrow(() -> new IllegalArgumentException("등록되지 않은 사용자입니다."));

		Event event = Event.of(senior, title.trim(),
				(content == null || content.isBlank()) ? null : content.trim(), eventDate);

		for (MultipartFile file : actual) {
			String storageKey = photoStorage.store(file);
			String originalName = file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename();
			event.addPhoto(Photo.of(storageKey, originalName, file.getContentType(), file.getSize()));
		}

		Event saved = eventRepository.save(event);

		// 저장이 확정된 뒤에 담당 직원에게 메일이 나간다 (PhotoNotifier 참고).
		// 사진 10장을 보내도 메일은 한 통이다.
		eventPublisher.publishEvent(new PhotoUploadedEvent(
				saved.getId(), senior.getName(),
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

	/** 직원이 행사 정보를 고친다. 사진과 보낸 분은 그대로 둔다. */
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
