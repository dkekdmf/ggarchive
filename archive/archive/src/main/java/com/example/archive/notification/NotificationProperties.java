package com.example.archive.notification;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림 설정. archive.notify.* 로 지정한다.
 *
 * @param enabled 꺼져 있으면 메일을 보내지 않고 로그만 남긴다. 메일 서버가 준비되기 전 기본값.
 * @param to      받는 직원 메일 주소들
 * @param from    보내는 사람 주소
 * @param baseUrl 메일 본문에 넣을 확인 화면 주소. 예: https://photo.example.go.kr
 */
@ConfigurationProperties(prefix = "archive.notify")
public record NotificationProperties(
		boolean enabled,
		List<String> to,
		String from,
		String baseUrl) {

	public NotificationProperties {
		to = (to == null) ? List.of() : to;
		from = (from == null || from.isBlank()) ? "no-reply@localhost" : from;
		baseUrl = (baseUrl == null) ? "" : baseUrl.replaceAll("/+$", "");
	}

	/** 켜져 있고 받는 사람도 있어야 실제로 보낸다. */
	public boolean canSend() {
		return enabled && !to.isEmpty();
	}
}
