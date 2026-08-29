package com.example.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.example.archive.service.PhotoService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 알림을 켠 상태에서 사진을 올리면 메일이 나가는지 확인한다. 실제 메일 서버는 쓰지 않는다. */
@SpringBootTest
@TestPropertySource(properties = {
		"archive.notify.enabled=true",
		"archive.notify.to=staff@example.com",
		"archive.notify.from=no-reply@example.com",
		"archive.notify.base-url=https://photo.example.go.kr",
		"spring.mail.username=no-reply@example.com"
})
class PhotoNotifierTest {

	@MockitoBean
	private JavaMailSender mailSender;

	@Autowired
	private PhotoService photoService;

	private MockMultipartFile jpeg(String name) {
		return new MockMultipartFile("photos", name, MediaType.IMAGE_JPEG_VALUE, "bytes".getBytes());
	}

	@Test
	void 행사가_들어오면_담당자에게_메일이_나간다() {
		Long seniorId = photoService.identify("최복순").getId();

		photoService.submit(seniorId, "경로당 생신잔치", "즐거웠습니다",
				LocalDate.of(2026, 8, 14), List.of(jpeg("a.jpg")));

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		// 메일은 다른 스레드에서 나가므로 잠시 기다린다
		await().atMost(Duration.ofSeconds(5))
				.untilAsserted(() -> verify(mailSender).send(sent.capture()));

		SimpleMailMessage message = sent.getValue();
		assertThat(message.getTo()).containsExactly("staff@example.com");
		assertThat(message.getSubject()).contains("경로당 생신잔치").contains("최복순");
		assertThat(message.getText())
				.contains("경로당 생신잔치")
				.contains("2026년 8월 14일")
				.contains("최복순")
				.contains("1장")
				.contains("https://photo.example.go.kr/gallery");
	}

	@Test
	void 사진을_여러_장_보내도_메일은_한_통만_나간다() {
		Long seniorId = photoService.identify("한묶음").getId();

		photoService.submit(seniorId, "가을 나들이", null, LocalDate.now(),
				List.of(jpeg("1.jpg"), jpeg("2.jpg"), jpeg("3.jpg")));

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		await().atMost(Duration.ofSeconds(5))
				.untilAsserted(() -> verify(mailSender).send(sent.capture()));

		assertThat(sent.getValue().getSubject()).contains("3장");
		assertThat(sent.getValue().getText()).contains("3장");
	}

	@Test
	void 메일_서버가_죽어_있어도_사진_업로드는_성공한다() {
		Mockito.doThrow(new MailSendException("서버 없음"))
				.when(mailSender).send(any(SimpleMailMessage.class));

		Long seniorId = photoService.identify("정말순").getId();

		// 예외가 위로 튀어나오지 않아야 한다
		assertThat(photoService.submit(seniorId, "메일 실패 시험", null, LocalDate.now(),
				List.of(jpeg("b.jpg"))).getId()).isNotNull();
	}
}
