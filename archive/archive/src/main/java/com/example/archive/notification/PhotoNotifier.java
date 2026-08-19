package com.example.archive.notification;

import jakarta.annotation.PostConstruct;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사진이 들어오면 담당 직원에게 메일로 알린다.
 *
 * <p>두 가지를 지킨다:
 * <ul>
 *   <li>AFTER_COMMIT — DB 저장이 확정된 뒤에만 보낸다. 저장이 실패했는데 메일만 가는 일이 없도록.</li>
 *   <li>@Async + 예외 삼키기 — 메일 서버가 죽어 있어도 어르신의 업로드는 이미 끝나 있다.</li>
 * </ul>
 */
@Component
public class PhotoNotifier {

	private static final Logger log = LoggerFactory.getLogger(PhotoNotifier.class);
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M월 d일 HH시 mm분");
	private static final DateTimeFormatter EVENT_DAY = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

	/** 메일 설정이 없으면 이 빈 자체가 없다. 그때도 서비스는 정상 동작해야 하므로 선택적으로 받는다. */
	private final ObjectProvider<JavaMailSender> mailSender;
	private final NotificationProperties properties;
	private final String mailUsername;

	public PhotoNotifier(ObjectProvider<JavaMailSender> mailSender, NotificationProperties properties,
			@Value("${spring.mail.username:}") String mailUsername) {
		this.mailSender = mailSender;
		this.properties = properties;
		this.mailUsername = mailUsername;
	}

	/** 설정이 반쯤 된 상태로 조용히 실패하는 일이 없도록, 뜰 때 바로 알려 준다. */
	@PostConstruct
	void checkConfiguration() {
		if (!properties.enabled()) {
			log.info("사진 도착 알림 메일이 꺼져 있습니다 (archive.notify.enabled=false).");
			return;
		}
		if (properties.to().isEmpty()) {
			log.warn("알림은 켜져 있으나 받는 사람이 없습니다. archive.notify.to 에 메일 주소를 넣으세요.");
			return;
		}
		if (mailUsername.isBlank()) {
			log.warn("알림은 켜져 있으나 SMTP 계정(MAIL_USERNAME)이 비어 있어 메일이 나가지 않습니다. "
					+ "MAIL_USERNAME / MAIL_PASSWORD 환경변수를 설정하세요.");
			return;
		}
		log.info("사진 도착 알림 메일 준비 완료. 받는 사람: {}", properties.to());
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPhotoUploaded(PhotoUploadedEvent event) {
		if (!properties.canSend()) {
			log.info("[알림 꺼짐] {} 님이 '{}' 행사 사진 {}장 보냄 (행사 번호 {})",
					event.seniorName(), event.title(), event.photoCount(), event.eventId());
			return;
		}

		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			log.warn("메일 서버 설정(spring.mail.host)이 없어 알림을 보내지 못했습니다 (행사 번호 {}).", event.eventId());
			return;
		}

		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(properties.from());
			message.setTo(properties.to().toArray(new String[0]));
			message.setSubject("[사진 접수] %s - %s 님 (사진 %d장)"
					.formatted(event.title(), event.seniorName(), event.photoCount()));
			message.setText(body(event));

			sender.send(message);
			log.info("사진 도착 알림 메일 발송 (행사 번호 {}, 받는 사람 {})", event.eventId(), properties.to());
		} catch (Exception e) {
			// 메일이 안 나가도 사진은 이미 안전하게 저장돼 있다. 로그만 남기고 넘어간다.
			log.error("사진 도착 알림 메일을 보내지 못했습니다 (행사 번호 {}). 확인 화면에서는 정상적으로 보입니다.",
					event.eventId(), e);
		}
	}

	private String body(PhotoUploadedEvent event) {
		StringBuilder text = new StringBuilder()
				.append("어르신이 행사 사진을 보내셨습니다.\n\n")
				.append("행사 제목 : ").append(event.title()).append("\n")
				.append("행사 날짜 : ").append(event.eventDate().format(EVENT_DAY)).append("\n")
				.append("보낸 분   : ").append(event.seniorName()).append("\n")
				.append("사진 장수 : ").append(event.photoCount()).append("장\n")
				.append("받은 때   : ").append(event.createdAt().format(WHEN)).append("\n");

		if (!properties.baseUrl().isBlank()) {
			text.append("\n아래 주소에서 확인하세요.\n")
					.append(properties.baseUrl()).append("/gallery\n");
		}
		return text.toString();
	}
}
