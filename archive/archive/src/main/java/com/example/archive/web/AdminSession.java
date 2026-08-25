package com.example.archive.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 관리자로 로그인했는지 한 곳에서 판단한다. */
public final class AdminSession {

	static final String KEY = "adminLoggedIn";

	private AdminSession() {
	}

	public static boolean isAdmin(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return session != null && Boolean.TRUE.equals(session.getAttribute(KEY));
	}
}
