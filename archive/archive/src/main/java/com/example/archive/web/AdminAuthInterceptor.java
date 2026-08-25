package com.example.archive.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /admin 아래는 비밀번호를 넣은 직원만 쓸 수 있다.
 * 사진을 "보는" 것은 /gallery 에서 누구나 가능하고, 여기는 고치고 지우는 곳이다.
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		if (AdminSession.isAdmin(request)) {
			return true;
		}
		response.sendRedirect("/admin/login");
		return false;
	}
}
