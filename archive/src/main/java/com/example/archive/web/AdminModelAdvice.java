package com.example.archive.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 화면에서 "지금 관리자로 들어와 있는지"를 알 수 있게 한다.
 * 화면은 이 값으로 고치기·지우기 단추를 보여 줄지 정한다.
 */
@ControllerAdvice
public class AdminModelAdvice {

	@ModelAttribute("isAdmin")
	public boolean isAdmin(HttpServletRequest request) {
		return AdminSession.isAdmin(request);
	}
}
