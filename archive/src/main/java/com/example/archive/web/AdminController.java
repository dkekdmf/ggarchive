package com.example.archive.web;

import com.example.archive.service.PhotoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 직원용 관리 기능. 비밀번호를 넣어야 들어올 수 있고, 여기서만 고치고 지울 수 있다.
 * 사진을 보는 것 자체는 /gallery 에서 누구나 할 수 있다.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

	private final PhotoService photoService;
	private final String adminPassword;

	public AdminController(PhotoService photoService,
			@Value("${archive.admin.password}") String adminPassword) {
		this.photoService = photoService;
		this.adminPassword = adminPassword;
	}

	@GetMapping("/login")
	public String loginForm(HttpServletRequest request) {
		return AdminSession.isAdmin(request) ? "redirect:/gallery" : "admin/login";
	}

	@PostMapping("/login")
	public String login(@RequestParam String password, HttpSession session, RedirectAttributes redirect) {
		if (!adminPassword.equals(password)) {
			redirect.addFlashAttribute("error", "비밀번호가 맞지 않습니다.");
			return "redirect:/admin/login";
		}
		session.setAttribute(AdminSession.KEY, true);
		return "redirect:/gallery";
	}

	@PostMapping("/logout")
	public String logout(HttpSession session) {
		session.removeAttribute(AdminSession.KEY);
		return "redirect:/gallery";
	}

	/** 로그인한 채로 /admin 에 들어오면 목록으로 보낸다. 관리 단추는 목록·상세에 함께 나온다. */
	@GetMapping
	public String home() {
		return "redirect:/gallery";
	}

	@GetMapping("/events/{id}/edit")
	public String editForm(@PathVariable Long id,
			@RequestParam(required = false) String back,
			Model model) {

		model.addAttribute("event", photoService.getEvent(id));
		model.addAttribute("backUrl", safeBack(back));
		model.addAttribute("today", LocalDate.now());
		return "admin/edit";
	}

	@PostMapping("/events/{id}/edit")
	public String edit(@PathVariable Long id,
			@RequestParam String title,
			@RequestParam(required = false) String content,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
			@RequestParam(required = false) String back,
			RedirectAttributes redirect) {

		try {
			photoService.updateEvent(id, title, content, eventDate);
		} catch (IllegalArgumentException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/admin/events/" + id + "/edit";
		}
		redirect.addFlashAttribute("notice", "행사 정보를 고쳤습니다.");
		return "redirect:/gallery/events/" + id;
	}

	/** 상세 화면에서 항목 하나만 고쳐 저장한다. */
	@PostMapping("/events/{id}/field")
	public String editField(@PathVariable Long id,
			@RequestParam String field,
			@RequestParam(required = false) String value,
			@RequestParam(required = false) String back,
			RedirectAttributes redirect) {

		try {
			photoService.updateField(id, field, value);
			redirect.addFlashAttribute("notice", "%s을(를) 고쳤습니다.".formatted(label(field)));
		} catch (IllegalArgumentException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/gallery/events/" + id + "?back=" + encode(safeBack(back));
	}

	private String label(String field) {
		return switch (field) {
			case "title" -> "행사 이름";
			case "eventDate" -> "행사한 날";
			case "content" -> "행사 내용";
			default -> field;
		};
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	@PostMapping("/events/{id}/delete")
	public String deleteEvent(@PathVariable Long id,
			@RequestParam(required = false) String back,
			RedirectAttributes redirect) {

		String title = photoService.deleteEvent(id);
		redirect.addFlashAttribute("notice", "'%s' 행사를 사진까지 지웠습니다.".formatted(title));
		return "redirect:" + safeBack(back);
	}

	@PostMapping("/photos/{id}/delete")
	public String deletePhoto(@PathVariable Long id, RedirectAttributes redirect) {
		Long eventId = photoService.deletePhoto(id);
		redirect.addFlashAttribute("notice", "사진 한 장을 지웠습니다.");
		return "redirect:/gallery/events/" + eventId;
	}

	/** 바깥 주소로 튕겨 나가지 않도록 우리 화면만 허용한다. */
	private String safeBack(String back) {
		return (back == null || !back.startsWith("/gallery")) ? "/gallery" : back;
	}
}
