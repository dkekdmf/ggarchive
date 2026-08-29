package com.example.archive.web;

import com.example.archive.domain.Event;
import com.example.archive.domain.Senior;
import com.example.archive.service.PhotoService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 어르신이 쓰는 화면. 글씨를 크게, 한 화면에 버튼 하나만 보이도록 했다.
 */
@Controller
public class UploadController {

	static final String SESSION_SENIOR_ID = "seniorId";
	static final String SESSION_SENIOR_NAME = "seniorName";

	private final PhotoService photoService;

	public UploadController(PhotoService photoService) {
		this.photoService = photoService;
	}

	/** 1단계: 이름 + 전화 뒷 4자리. 이미 확인된 분은 곧장 사진 화면으로 보낸다. */
	@GetMapping("/")
	public String home(HttpSession session) {
		if (session.getAttribute(SESSION_SENIOR_ID) != null) {
			return "redirect:/upload";
		}
		return "identify";
	}

	@PostMapping("/identify")
	public String identify(@RequestParam String name,
			HttpSession session,
			RedirectAttributes redirect) {

		if (name == null || name.isBlank()) {
			redirect.addFlashAttribute("error", "이름을 적어 주세요.");
			return "redirect:/";
		}

		Senior senior = photoService.identify(name);
		session.setAttribute(SESSION_SENIOR_ID, senior.getId());
		session.setAttribute(SESSION_SENIOR_NAME, senior.getName());
		return "redirect:/upload";
	}

	/** 2단계: 커다란 "사진 찍기" 버튼 하나만 있는 화면 */
	@GetMapping("/upload")
	public String uploadForm(HttpSession session, Model model) {
		Long seniorId = (Long) session.getAttribute(SESSION_SENIOR_ID);
		if (seniorId == null) {
			return "redirect:/";
		}
		model.addAttribute("seniorName", session.getAttribute(SESSION_SENIOR_NAME));
		model.addAttribute("maxCount", photoService.getMaxPhotosPerUpload());
		model.addAttribute("today", LocalDate.now());
		return "upload";
	}

	@PostMapping("/upload")
	public String upload(@RequestParam(value = "photos", required = false) List<MultipartFile> photos,
			@RequestParam(required = false) String title,
			@RequestParam(required = false) String content,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
			HttpSession session,
			RedirectAttributes redirect) {

		Long seniorId = (Long) session.getAttribute(SESSION_SENIOR_ID);
		if (seniorId == null) {
			return "redirect:/";
		}
		Event saved;
		try {
			saved = photoService.submit(seniorId, title, content, eventDate, photos);
		} catch (IllegalArgumentException e) {
			// 제목 누락이나 장수 초과처럼 어르신이 고칠 수 있는 문제는 그대로 알려 드린다
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

	/** 3단계: 큰 체크 표시로 "잘 보냈습니다"를 확실히 알려 준다. */
	@GetMapping("/done")
	public String done(HttpSession session, Model model) {
		if (session.getAttribute(SESSION_SENIOR_ID) == null) {
			return "redirect:/";
		}
		model.addAttribute("seniorName", session.getAttribute(SESSION_SENIOR_NAME));
		return "done";
	}

	@PostMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/";
	}
}
