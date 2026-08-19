package com.example.archive.web;

import com.example.archive.domain.Event;
import com.example.archive.domain.Photo;
import com.example.archive.service.PhotoService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

/**
 * 올라온 행사 사진을 보는 화면. 비밀번호 없이 누구나 볼 수 있다.
 */
@Controller
@RequestMapping("/gallery")
public class GalleryController {

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

		Page<Event> events = photoService.search(name, from, to, onlyUnchecked, page, 12);
		model.addAttribute("events", events);
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
			String filename = URLEncoder.encode(
					photo.getSenior().getDisplayName() + "_" + photo.getId() + "_" + photo.getOriginalName(),
					StandardCharsets.UTF_8);
			response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename);
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
