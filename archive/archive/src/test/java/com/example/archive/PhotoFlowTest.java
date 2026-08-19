package com.example.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.archive.repository.EventRepository;
import com.example.archive.repository.PhotoRepository;
import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PhotoFlowTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private PhotoRepository photoRepository;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private com.example.archive.repository.SeniorRepository seniorRepository;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private MockHttpSession loginAs(MockMvc mvc, String name) throws Exception {
		return (MockHttpSession) mvc.perform(post("/identify").param("name", name))
				.andReturn().getRequest().getSession();
	}

	private MockMultipartFile jpeg(String filename) {
		return new MockMultipartFile("photos", filename, MediaType.IMAGE_JPEG_VALUE, "fake-bytes".getBytes());
	}

	@Test
	void 어르신은_행사정보와_사진을_함께_보낸다() throws Exception {
		MockMvc mvc = mockMvc();
		long beforePhotos = photoRepository.count();
		long beforeEvents = eventRepository.count();

		mvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("사진 보내기")));

		MockHttpSession session = loginAs(mvc, "김순자");

		mvc.perform(get("/upload").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("어떤 행사인가요?")));

		mvc.perform(multipart("/upload")
						.file(jpeg("1.jpg"))
						.file(jpeg("2.jpg"))
						.param("title", "경로당 생신잔치")
						.param("content", "스무 분이 모였습니다")
						.param("eventDate", LocalDate.now().minusDays(1).toString())
						.session(session))
				.andExpect(redirectedUrl("/done"));

		assertThat(eventRepository.count()).isEqualTo(beforeEvents + 1);
		assertThat(photoRepository.count()).isEqualTo(beforePhotos + 2);

		mvc.perform(get("/done").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("경로당 생신잔치")))
				.andExpect(content().string(Matchers.containsString("2장을 보냈습니다")));
	}

	@Test
	void 행사_이름이_없으면_되돌려_보낸다() throws Exception {
		MockMvc mvc = mockMvc();
		long before = eventRepository.count();
		MockHttpSession session = loginAs(mvc, "이말순");

		mvc.perform(multipart("/upload")
						.file(jpeg("1.jpg"))
						.param("eventDate", LocalDate.now().toString())
						.session(session))
				.andExpect(redirectedUrl("/upload"));

		assertThat(eventRepository.count()).isEqualTo(before);
	}

	@Test
	void 오지_않은_날짜는_고를_수_없다() throws Exception {
		MockMvc mvc = mockMvc();
		long before = eventRepository.count();
		MockHttpSession session = loginAs(mvc, "박영수");

		mvc.perform(multipart("/upload")
						.file(jpeg("1.jpg"))
						.param("title", "미래 행사")
						.param("eventDate", LocalDate.now().plusDays(1).toString())
						.session(session))
				.andExpect(redirectedUrl("/upload"));

		assertThat(eventRepository.count()).isEqualTo(before);
	}

	@Test
	void 사진이_없으면_되돌려_보낸다() throws Exception {
		MockMvc mvc = mockMvc();
		long before = eventRepository.count();
		MockHttpSession session = loginAs(mvc, "강복자");

		// 고르지 않은 입력이 빈 값으로 함께 올라오는 상황
		mvc.perform(multipart("/upload")
						.file(new MockMultipartFile("photos", "", MediaType.IMAGE_JPEG_VALUE, new byte[0]))
						.param("title", "사진 없는 행사")
						.param("eventDate", LocalDate.now().toString())
						.session(session))
				.andExpect(redirectedUrl("/upload"));

		assertThat(eventRepository.count()).isEqualTo(before);
	}

	@Test
	void 정해진_장수보다_많이_보내면_한_장도_저장하지_않는다() throws Exception {
		MockMvc mvc = mockMvc();
		long beforePhotos = photoRepository.count();
		long beforeEvents = eventRepository.count();
		MockHttpSession session = loginAs(mvc, "송삼순");

		MockMultipartHttpServletRequestBuilder request = multipart("/upload");
		for (int i = 0; i < 11; i++) {
			request = request.file(jpeg(i + ".jpg"));
		}

		mvc.perform(request
						.param("title", "너무 많은 사진")
						.param("eventDate", LocalDate.now().toString())
						.session(session))
				.andExpect(redirectedUrl("/upload"));

		assertThat(eventRepository.count()).isEqualTo(beforeEvents);
		assertThat(photoRepository.count()).isEqualTo(beforePhotos);
	}

	@Test
	void 이름이_비어_있으면_다시_묻는다() throws Exception {
		mockMvc().perform(post("/identify").param("name", "   "))
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void 같은_이름으로_다시_들어오면_같은_사람으로_묶인다() throws Exception {
		MockMvc mvc = mockMvc();

		mvc.perform(multipart("/upload").file(jpeg("a.jpg"))
						.param("title", "첫 번째 행사")
						.param("eventDate", LocalDate.now().toString())
						.session(loginAs(mvc, "한사람")))
				.andExpect(redirectedUrl("/done"));

		long seniorsAfterFirst = seniorRepository.count();

		mvc.perform(multipart("/upload").file(jpeg("b.jpg"))
						.param("title", "두 번째 행사")
						.param("eventDate", LocalDate.now().toString())
						.session(loginAs(mvc, "한사람")))
				.andExpect(redirectedUrl("/done"));

		// 같은 이름이므로 사람이 새로 등록되면 안 된다
		assertThat(seniorRepository.count()).isEqualTo(seniorsAfterFirst);
		assertThat(seniorRepository.findByName("한사람")).isPresent();
	}

	@Test
	void 사진_목록은_비밀번호_없이_누구나_본다() throws Exception {
		MockMvc mvc = mockMvc();

		mvc.perform(multipart("/upload")
						.file(jpeg("a.jpg"))
						.param("title", "가을 나들이")
						.param("content", "단풍 구경을 갔습니다")
						.param("eventDate", LocalDate.now().toString())
						.session(loginAs(mvc, "정말순")))
				.andExpect(redirectedUrl("/done"));

		// 로그인하지 않은 새 방문자
		mvc.perform(get("/gallery"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("가을 나들이")))
				.andExpect(content().string(Matchers.containsString("단풍 구경을 갔습니다")))
				.andExpect(content().string(Matchers.containsString("정말순")));
	}

	@Test
	void 사진_파일도_비밀번호_없이_열린다() throws Exception {
		MockMvc mvc = mockMvc();

		mvc.perform(multipart("/upload")
						.file(jpeg("a.jpg"))
						.param("title", "사진 열람 시험")
						.param("eventDate", LocalDate.now().toString())
						.session(loginAs(mvc, "열람시험")))
				.andExpect(redirectedUrl("/done"));

		// 방금 올린 사진이 가장 마지막 번호를 갖는다 (관계를 타면 세션이 없어 읽을 수 없다)
		Long photoId = photoRepository.findAll().stream()
				.map(p -> p.getId())
				.max(Long::compareTo).orElseThrow();

		mvc.perform(get("/gallery/photos/{id}/file", photoId))
				.andExpect(status().isOk());
	}

	@Test
	void 행사_단위로_확인_처리한다() throws Exception {
		MockMvc mvc = mockMvc();

		mvc.perform(multipart("/upload")
						.file(jpeg("a.jpg"))
						.param("title", "확인 처리 시험")
						.param("eventDate", LocalDate.now().toString())
						.session(loginAs(mvc, "최복순")))
				.andExpect(redirectedUrl("/done"));

		Long eventId = eventRepository.findAll().stream()
				.filter(e -> e.getTitle().equals("확인 처리 시험"))
				.findFirst().orElseThrow().getId();

		mvc.perform(post("/gallery/events/{id}/check", eventId))
				.andExpect(redirectedUrl("/gallery"));

		assertThat(eventRepository.findById(eventId).orElseThrow().isChecked()).isTrue();
	}
}
