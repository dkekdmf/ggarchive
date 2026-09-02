package com.example.archive.web;

import com.example.archive.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 직원용 관리 기능. 비밀번호를 넣어야 들어올 수 있고, 여기서만 고치고 지울 수 있다.
 * 사진을 보는 것 자체는 /gallery 에서 누구나 할 수 있다.
 */
@Tag(name = "Admin", description = "관리자 로그인 및 행사·사진 관리 API")
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

    @Operation(summary = "로그인 화면 이동", description = "세션이 없으면 로그인 폼으로, 이미 로그인되어 있으면 /gallery로 이동합니다.")
    @GetMapping("/login")
    public String loginForm(HttpServletRequest request) {
        return AdminSession.isAdmin(request) ? "redirect:/gallery" : "admin/login";
    }

    @Operation(summary = "관리자 로그인", description = "비밀번호를 검증하고 일치하면 세션을 등록합니다.")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String login(
            @Parameter(description = "관리자 비밀번호", required = true) @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirect) {
        if (!adminPassword.equals(password)) {
            redirect.addFlashAttribute("error", "비밀번호가 맞지 않습니다.");
            return "redirect:/admin/login";
        }
        session.setAttribute(AdminSession.KEY, true);
        return "redirect:/gallery";
    }

    @Operation(summary = "관리자 로그아웃", description = "현재 세션의 관리자 인증 상태를 해제합니다.")
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute(AdminSession.KEY);
        return "redirect:/gallery";
    }

    @Operation(summary = "관리자 메인", description = "갤러리 목록 페이지(/gallery)로 리다이렉트합니다.")
    @GetMapping
    public String home() {
        return "redirect:/gallery";
    }

    @Operation(summary = "행사 수정 화면 이동", description = "선택한 행사의 수정 폼 화면을 조회합니다.")
    @GetMapping("/events/{id}/edit")
    public String editForm(
            @Parameter(description = "행사 식별자(ID)", required = true) @PathVariable Long id,
            @Parameter(description = "수정 완료 후 돌아갈 이전 주소") @RequestParam(required = false) String back,
            Model model) {

        model.addAttribute("event", photoService.getEvent(id));
        model.addAttribute("backUrl", safeBack(back));
        model.addAttribute("today", LocalDate.now());
        return "admin/edit";
    }

    @Operation(summary = "행사 정보 전체 수정", description = "행사의 제목, 내용, 진행 날짜를 수정합니다.")
    @PostMapping(value = "/events/{id}/edit", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String edit(
            @Parameter(description = "행사 ID", required = true) @PathVariable Long id,
            @Parameter(description = "수정할 행사 제목", required = true) @RequestParam String title,
            @Parameter(description = "수정할 행사 설명/내용") @RequestParam(required = false) String content,
            @Parameter(description = "행사 진행 일자 (YYYY-MM-DD)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @Parameter(description = "이전 주소") @RequestParam(required = false) String back,
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

    @Operation(summary = "행사 개별 필드 수정", description = "상세 화면에서 단일 필드(제목, 행사일, 내용 등)를 인라인으로 수정합니다.")
    @PostMapping(value = "/events/{id}/field", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String editField(
            @Parameter(description = "행사 ID", required = true) @PathVariable Long id,
            @Parameter(description = "수정 대상 필드명 (title, eventDate, content)", required = true) @RequestParam String field,
            @Parameter(description = "수정할 값") @RequestParam(required = false) String value,
            @Parameter(description = "이전 주소") @RequestParam(required = false) String back,
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

    @Operation(summary = "행사 및 소속 사진 일괄 삭제", description = "행사 정보와 등록된 사진 데이터를 모두 삭제합니다.")
    @PostMapping("/events/{id}/delete")
    public String deleteEvent(
            @Parameter(description = "삭제할 행사 ID", required = true) @PathVariable Long id,
            @Parameter(description = "삭제 후 돌아갈 주소") @RequestParam(required = false) String back,
            RedirectAttributes redirect) {

        String title = photoService.deleteEvent(id);
        redirect.addFlashAttribute("notice", "'%s' 행사를 사진까지 지웠습니다.".formatted(title));
        return "redirect:" + safeBack(back);
    }

    @Operation(summary = "행사 사진 추가 업로드", description = "기존 행사에 1장 이상의 사진 파일을 추가합니다.")
    @PostMapping(value = "/events/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String addPhotos(
            @Parameter(description = "행사 ID", required = true) @PathVariable Long id,
            @Parameter(description = "추가 업로드할 이미지 파일 목록") @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
            @Parameter(description = "이전 주소") @RequestParam(required = false) String back,
            RedirectAttributes redirect) {

        try {
            int added = photoService.addPhotos(id, photos).getPhotoCount();
            redirect.addFlashAttribute("notice", "사진을 넣었습니다. 지금 %d장입니다.".formatted(added));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/gallery/events/" + id + "?back=" + encode(safeBack(back));
    }

    @Operation(summary = "사진 단건 삭제", description = "특정 사진 한 장을 삭제합니다.")
    @PostMapping("/photos/{id}/delete")
    public String deletePhoto(
            @Parameter(description = "삭제할 사진 ID", required = true) @PathVariable Long id,
            RedirectAttributes redirect) {
        Long eventId = photoService.deletePhoto(id);
        redirect.addFlashAttribute("notice", "사진 한 장을 지웠습니다.");
        return "redirect:/gallery/events/" + eventId;
    }

    private String safeBack(String back) {
        return (back == null || !back.startsWith("/gallery")) ? "/gallery" : back;
    }
}