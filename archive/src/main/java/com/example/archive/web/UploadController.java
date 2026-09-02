package com.example.archive.web;

import com.example.archive.domain.Event;
import com.example.archive.service.PhotoService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 사진을 올리는 화면. 로그인이 없고, 한 화면에서 행사 정보와 사진을 함께 받는다.
 *
 * <p>순서는 "행사 정보 → 사진"이다. 무엇에 쓸 사진인지 먼저 정해야
 * 어떤 사진을 골라야 할지도 분명해지기 때문이다.
 */
@Controller
public class UploadController {

	private final PhotoService photoService;

	public UploadController(PhotoService photoService) {
		this.photoService = photoService;
	}

	/**
	 * @param date 달력에서 누른 날. 사용 시작일에 미리 채워 준다.
	 */
	@GetMapping("/upload")
	public String uploadForm(@RequestParam(required = false) String date, Model model) {

		LocalDate today = LocalDate.now();
		// 앞으로 있을 행사도 미리 적을 수 있다. 주소를 잘못 건드린 경우만 오늘로 시작한다.
		LocalDate picked = parseOrNull(date);
		LocalDate startDate = (picked == null) ? today : picked;

		model.addAttribute("maxCount", photoService.getMaxPhotosPerUpload());
		model.addAttribute("today", today);
		model.addAttribute("startDate", startDate);
		return "upload";
	}

	@PostMapping("/upload")
	public String upload(@RequestParam(required = false) String title,
			@RequestParam(required = false) String company,
			@RequestParam(required = false) String place,
			@RequestParam(required = false) String purpose,
			@RequestParam(required = false) String eventDate,
			@RequestParam(required = false) String usageEnd,
			@RequestParam(required = false) String content,
			@RequestParam(value = "photos", required = false) List<MultipartFile> photos,
			RedirectAttributes redirect) {

		Event saved;
		try {
			LocalDate start = parseOrNull(eventDate);
			if (start == null) {
				throw new IllegalArgumentException("사용 날짜를 2026-09-01 처럼 적어 주세요.");
			}
			saved = photoService.submit(title, company, place, purpose,
					start, parseOrNull(usageEnd), content, photos);
		} catch (IllegalArgumentException e) {
			// 빠뜨린 칸처럼 스스로 고칠 수 있는 문제는 그대로 알려 드린다
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/upload";
		} catch (RuntimeException e) {
			redirect.addFlashAttribute("error", "사진을 보내지 못했어요. 다시 한 번 눌러 주세요.");
			return "redirect:/upload";
		}

		redirect.addFlashAttribute("sentCount", saved.getPhotoCount());
		redirect.addFlashAttribute("sentTitle", saved.getTitle());
		return "redirect:/done";
	}

	/**
	 * 날짜를 손으로 적어 받으므로 흔한 형태를 두루 받아 준다.
	 * 2026-09-01, 2026.09.01, 2026/9/1, 20260901 을 모두 같은 날로 읽는다.
	 */
	private LocalDate parseOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		// 숫자 덩어리만 뽑아낸다. 구분자가 무엇이든 상관없다.
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (char c : value.toCharArray()) {
			if (Character.isDigit(c)) {
				current.append(c);
			} else if (current.length() > 0) {
				parts.add(current.toString());
				current.setLength(0);
			}
		}
		if (current.length() > 0) {
			parts.add(current.toString());
		}

		String year;
		String month;
		String day;

		if (parts.size() == 3) {
			// 2026-9-1, 2026.09.01, 2026/9/1
			year = parts.get(0);
			month = parts.get(1);
			day = parts.get(2);
		} else if (parts.size() == 1 && parts.get(0).length() == 8) {
			// 20260901
			String only = parts.get(0);
			year = only.substring(0, 4);
			month = only.substring(4, 6);
			day = only.substring(6, 8);
		} else {
			return null;
		}

		try {
			return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** 다 보냈다는 것을 큰 표시로 알려 준다. 곧바로 들어오면 첫 화면으로 보낸다. */
	@GetMapping("/done")
	public String done(Model model) {
		if (!model.containsAttribute("sentCount")) {
			return "redirect:/";
		}
		return "done";
	}
}
