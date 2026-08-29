package com.example.archive.web;

import com.example.archive.domain.Event;
import com.example.archive.domain.Photo;
import com.example.archive.service.PhotoService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 올라온 행사 사진을 보는 화면. 비밀번호 없이 누구나 볼 수 있다.
 */
@Controller
@RequestMapping("/gallery")
public class GalleryController {

	private static final Logger log = LoggerFactory.getLogger(GalleryController.class);

	private final PhotoService photoService;

	public GalleryController(PhotoService photoService) {
		this.photoService = photoService;
	}

	@GetMapping
	public String list(@RequestParam(required = false) String name,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "false") boolean onlyUnchecked,
			@RequestParam(defaultValue = "0") int page,
			Model model) {

		// 한 줄에 3개씩 놓이므로 3의 배수로 채운다
		Page<Event> events = photoService.search(name, from, to, onlyUnchecked, page, 18);
		model.addAttribute("events", events);
		model.addAttribute("groups", groupByEventDate(events));
		model.addAttribute("today", LocalDate.now());
		model.addAttribute("name", name);
		model.addAttribute("from", from);
		model.addAttribute("to", to);
		model.addAttribute("onlyUnchecked", onlyUnchecked);
		model.addAttribute("todayCount", photoService.countToday());
		model.addAttribute("uncheckedCount", photoService.countUnchecked());
		// "확인함" 을 누른 뒤 보던 검색 조건 그대로 돌아오게 한다
		model.addAttribute("currentUrl", buildListUrl(name, from, to, onlyUnchecked, page));
		return "gallery/list";
	}

	/**
	 * 행사를 "행사한 날"별로 묶는다. 조회가 이미 날짜 내림차순이라 넣는 순서가 곧 보여 줄 순서다.
	 * (LinkedHashMap 이어야 이 순서가 유지된다)
	 */
	private Map<LocalDate, List<Event>> groupByEventDate(Page<Event> events) {
		Map<LocalDate, List<Event>> grouped = new LinkedHashMap<>();
		for (Event event : events.getContent()) {
			grouped.computeIfAbsent(event.getEventDate(), day -> new ArrayList<>()).add(event);
		}
		return grouped;
	}

	private String buildListUrl(String name, LocalDate from, LocalDate to, boolean onlyUnchecked, int page) {
		StringBuilder url = new StringBuilder("/gallery?page=").append(page);
		if (name != null && !name.isBlank()) {
			url.append("&name=").append(URLEncoder.encode(name, StandardCharsets.UTF_8));
		}
		if (from != null) {
			url.append("&from=").append(from);
		}
		if (to != null) {
			url.append("&to=").append(to);
		}
		if (onlyUnchecked) {
			url.append("&onlyUnchecked=true");
		}
		return url.toString();
	}

	/**
	 * 한 달을 달력으로 본다. 행사가 없던 날이 빈칸으로 드러나서 월 단위로 훑기 좋다.
	 */
	@GetMapping("/calendar")
	public String calendar(@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month,
			Model model) {

		YearMonth current = (year == null || month == null)
				? YearMonth.now()
				: YearMonth.of(year, month);

		model.addAttribute("month", current);
		model.addAttribute("prev", current.minusMonths(1));
		model.addAttribute("next", current.plusMonths(1));
		model.addAttribute("weeks", buildWeeks(current));
		model.addAttribute("eventsByDay", photoService.eventsByDay(current));
		model.addAttribute("today", LocalDate.now());
		model.addAttribute("thisMonth", YearMonth.now());
		return "gallery/calendar";
	}

	/**
	 * 달력에 그릴 주 단위 날짜 표를 만든다.
	 * 첫 주와 마지막 주는 앞뒤 달 날짜로 채워 칸이 비지 않게 한다.
	 */
	private List<List<LocalDate>> buildWeeks(YearMonth month) {
		// 일요일부터 시작하는 달력. DayOfWeek 는 월요일이 1이라 일요일(7)을 0으로 바꿔 계산한다.
		LocalDate first = month.atDay(1);
		LocalDate start = first.minusDays(first.getDayOfWeek().getValue() % 7);

		LocalDate last = month.atEndOfMonth();
		LocalDate end = last.plusDays(6 - (last.getDayOfWeek().getValue() % 7));

		List<List<LocalDate>> weeks = new ArrayList<>();
		for (LocalDate day = start; !day.isAfter(end); ) {
			List<LocalDate> week = new ArrayList<>(7);
			for (int i = 0; i < 7; i++) {
				week.add(day);
				day = day.plusDays(1);
			}
			weeks.add(week);
		}
		return weeks;
	}

	/** 행사 하나를 자세히 본다. 사진 전체와 내용이 모두 나온다. */
	@GetMapping("/events/{id}")
	public String detail(@PathVariable Long id,
			@RequestParam(required = false) String back,
			Model model) {

		model.addAttribute("event", photoService.getEvent(id));
		model.addAttribute("backUrl", (back == null || !back.startsWith("/gallery")) ? "/gallery" : back);
		return "gallery/detail";
	}

	/**
	 * 한 행사의 사진을 통째로 압축해서 내려 준다.
	 * 사진이 여러 장이라 한 장씩 받게 하면 실무에서 쓰기 어렵다.
	 */
	@GetMapping("/events/{id}/photos.zip")
	public ResponseEntity<StreamingResponseBody> downloadAll(@PathVariable Long id) {
		Event event = photoService.getEvent(id);
		if (event.getPhotoCount() == 0) {
			return ResponseEntity.notFound().build();
		}

		String filename = "%s_%s.zip".formatted(
				safeName(event.getTitle()), event.getEventDate());

		StreamingResponseBody body = out -> {
			try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
				int index = 1;
				for (Photo photo : event.getPhotos()) {
					// 같은 이름이 섞여도 덮이지 않도록 번호를 붙인다
					zip.putNextEntry(new ZipEntry("%02d_%s".formatted(index++, safeName(photo.getOriginalName()))));
					try (InputStream in = photoService.loadFile(photo).getInputStream()) {
						in.transferTo(zip);
					} catch (IOException e) {
						// 파일 하나가 없어도 나머지는 받을 수 있게 넘어간다
						log.warn("압축에서 빠진 사진이 있습니다 (사진 번호 {})", photo.getId(), e);
					}
					zip.closeEntry();
				}
			}
		};

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, attachment(filename))
				.body(body);
	}

	/** 파일 이름에 쓰기 곤란한 글자를 걷어낸다. */
	private String safeName(String name) {
		return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
	}

	/** 한글 파일 이름이 깨지지 않게 규격에 맞춰 적는다. */
	private String attachment(String filename) {
		String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
		return "attachment; filename*=UTF-8''" + encoded;
	}

	/** 사진 원본을 그대로 내려 준다. 목록의 img 도 이 주소를 쓴다. */
	@GetMapping("/photos/{id}/file")
	@ResponseBody
	public ResponseEntity<Resource> file(@PathVariable Long id,
			@RequestParam(defaultValue = "false") boolean download) {

		Photo photo = photoService.getPhoto(id);
		Resource resource = photoService.loadFile(photo);

		ResponseEntity.BodyBuilder response = ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(photo.getContentType()));

		if (download) {
			response.header(HttpHeaders.CONTENT_DISPOSITION, attachment(
					safeName(photo.getSenior().getDisplayName() + "_" + photo.getId() + "_"
							+ photo.getOriginalName())));
		} else {
			response.header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");
		}
		return response.body(resource);
	}

	@PostMapping("/events/{id}/check")
	public String check(@PathVariable Long id, @RequestParam(required = false) String redirectTo) {
		photoService.toggleChecked(id);
		return "redirect:" + (redirectTo == null || !redirectTo.startsWith("/gallery") ? "/gallery" : redirectTo);
	}
}
