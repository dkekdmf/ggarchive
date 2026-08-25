package com.example.archive.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 행사 하나가 저장된 뒤 발행되는 사건. 사진을 여러 장 보내도 이 사건은 한 번만 발행된다.
 * 알림은 다른 스레드에서 처리되므로 엔티티를 그대로 넘기지 않고 필요한 값만 복사해서 담는다.
 *
 * @param eventId    행사 번호
 * @param photoCount 이번에 보낸 사진 장수
 */
public record PhotoUploadedEvent(
		Long eventId,
		String seniorName,
		String title,
		LocalDate eventDate,
		LocalDateTime createdAt,
		int photoCount) {
}
