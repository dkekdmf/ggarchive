package com.example.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.archive.repository.EventRepository;
import com.example.archive.repository.PhotoRepository;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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

		// 로그인하지 않은 새 방문자에게도 목록이 보인다 (요약만)
		mvc.perform(get("/gallery"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("가을 나들이")))
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
	void 목록은_행사한_날별로_묶여_나온다() throws Exception {
		MockMvc mvc = mockMvc();
		LocalDate today = LocalDate.now();
		LocalDate lastWeek = today.minusDays(7);

		// 같은 날 두 건, 다른 날 한 건
		var session = loginAs(mvc, "묶음시험");
		for (String title : new String[] {"오늘 행사 하나", "오늘 행사 둘"}) {
			mvc.perform(multipart("/upload").file(jpeg("a.jpg"))
							.param("title", title)
							.param("eventDate", today.toString())
							.session(session))
					.andExpect(redirectedUrl("/done"));
		}
		mvc.perform(multipart("/upload").file(jpeg("b.jpg"))
						.param("title", "지난주 행사")
						.param("eventDate", lastWeek.toString())
						.session(session))
				.andExpect(redirectedUrl("/done"));

		String html = mvc.perform(get("/gallery").param("name", "행사"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// 날짜 머리글과 건수, 오늘 표시가 나온다
		assertThat(html).contains("class=\"date-head\"").contains("오늘");

		// 오늘 것이 지난주 것보다 먼저 나온다 (날짜 내림차순)
		assertThat(html.indexOf("오늘 행사")).isLessThan(html.indexOf("지난주 행사"));
	}

	@Test
	void 달력에서_그_달의_행사를_본다() throws Exception {
		MockMvc mvc = mockMvc();
		LocalDate day = LocalDate.now().withDayOfMonth(10);

		mvc.perform(multipart("/upload").file(jpeg("a.jpg"))
						.param("title", "달력 시험 행사")
						.param("eventDate", day.toString())
						.session(loginAs(mvc, "달력시험")))
				.andExpect(redirectedUrl("/done"));

		// 그 달 달력에는 나온다
		mvc.perform(get("/gallery/calendar")
						.param("year", String.valueOf(day.getYear()))
						.param("month", String.valueOf(day.getMonthValue())))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("달력 시험 행사")))
				.andExpect(content().string(Matchers.containsString(
						day.getYear() + "년 " + day.getMonthValue() + "월")));

		// 다음 달 달력에는 안 나온다
		LocalDate nextMonth = day.plusMonths(1);
		mvc.perform(get("/gallery/calendar")
						.param("year", String.valueOf(nextMonth.getYear()))
						.param("month", String.valueOf(nextMonth.getMonthValue())))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.not(
						Matchers.containsString("달력 시험 행사"))));
	}

	@Test
	void 하루에_행사가_많으면_접히고_전체가_펼침목록에_담긴다() throws Exception {
		MockMvc mvc = mockMvc();
		// 다른 시험과 날짜가 겹치지 않게 이 시험만 쓰는 날을 고른다
		LocalDate day = LocalDate.now().withDayOfMonth(3);
		var session = loginAs(mvc, "많은행사");

		for (String title : new String[] {"첫째 행사", "둘째 행사", "셋째 행사", "넷째 행사"}) {
			mvc.perform(multipart("/upload").file(jpeg("a.jpg"))
							.param("title", title)
							.param("eventDate", day.toString())
							.session(session))
					.andExpect(redirectedUrl("/done"));
		}

		String html = mvc.perform(get("/gallery/calendar")
						.param("year", String.valueOf(day.getYear()))
						.param("month", String.valueOf(day.getMonthValue())))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// 칸에는 2건만 보이고 나머지는 접힌다
		assertThat(html).contains("class=\"day-more\"").contains("+2건");

		// 접힌 것을 눌렀을 때 나올 목록에는 네 건이 모두 들어 있다
		assertThat(html).contains("class=\"day-pop\"");
		for (String title : new String[] {"첫째 행사", "둘째 행사", "셋째 행사", "넷째 행사"}) {
			assertThat(html).contains(title);
		}
	}

	@Test
	void 달력과_목록은_서로_오갈_수_있다() throws Exception {
		MockMvc mvc = mockMvc();

		mvc.perform(get("/gallery"))
				.andExpect(content().string(Matchers.containsString("/gallery/calendar")));

		mvc.perform(get("/gallery/calendar"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("일")))
				.andExpect(content().string(Matchers.containsString("토")));
	}

	@Test
	void 목록에서_행사를_누르면_상세_화면이_열린다() throws Exception {
		MockMvc mvc = mockMvc();

		mvc.perform(multipart("/upload")
						.file(jpeg("a.jpg"))
						.file(jpeg("b.jpg"))
						.param("title", "상세 화면 시험")
						.param("content", "자세한 내용은 여기에서만 보인다")
						.param("eventDate", LocalDate.now().toString())
						.session(loginAs(mvc, "상세시험")))
				.andExpect(redirectedUrl("/done"));

		Long eventId = eventRepository.findAll().stream()
				.filter(e -> e.getTitle().equals("상세 화면 시험"))
				.findFirst().orElseThrow().getId();

		// 목록에는 상세로 가는 링크가 있고, 내용은 나오지 않는다
		mvc.perform(get("/gallery"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("/gallery/events/" + eventId)))
				.andExpect(content().string(Matchers.not(
						Matchers.containsString("자세한 내용은 여기에서만 보인다"))));

		// 상세 화면에는 내용과 사진이 모두 나온다
		mvc.perform(get("/gallery/events/{id}", eventId))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("상세 화면 시험")))
				.andExpect(content().string(Matchers.containsString("자세한 내용은 여기에서만 보인다")))
				.andExpect(content().string(Matchers.containsString("사진 2장")))
				.andExpect(content().string(Matchers.containsString("상세시험")));
	}

	private MockHttpSession loginAsAdmin(MockMvc mvc) throws Exception {
		return (MockHttpSession) mvc.perform(post("/admin/login").param("password", "test1234"))
				.andExpect(redirectedUrl("/gallery"))
				.andReturn().getRequest().getSession();
	}

	private Long uploadEvent(MockMvc mvc, String senior, String title) throws Exception {
		mvc.perform(multipart("/upload").file(jpeg("a.jpg")).file(jpeg("b.jpg"))
						.param("title", title)
						.param("content", "원래 내용")
						.param("eventDate", LocalDate.now().toString())
						.session(loginAs(mvc, senior)))
				.andExpect(redirectedUrl("/done"));

		return eventRepository.findAll().stream()
				.filter(e -> e.getTitle().equals(title))
				.findFirst().orElseThrow().getId();
	}

	@Test
	void 로그인하지_않으면_고치기_지우기를_할_수_없다() throws Exception {
		MockMvc mvc = mockMvc();
		Long eventId = uploadEvent(mvc, "권한시험", "권한 시험 행사");

		mvc.perform(get("/admin")).andExpect(redirectedUrl("/admin/login"));
		mvc.perform(get("/admin/events/{id}/edit", eventId)).andExpect(redirectedUrl("/admin/login"));
		mvc.perform(post("/admin/events/{id}/edit", eventId)
						.param("title", "몰래 고치기")
						.param("eventDate", LocalDate.now().toString()))
				.andExpect(redirectedUrl("/admin/login"));
		mvc.perform(post("/admin/events/{id}/delete", eventId)).andExpect(redirectedUrl("/admin/login"));

		// 아무것도 바뀌지 않았다
		assertThat(eventRepository.findById(eventId)).isPresent();
		assertThat(eventRepository.findById(eventId).orElseThrow().getTitle()).isEqualTo("권한 시험 행사");
	}

	@Test
	void 관리자가_아니면_고치기_단추가_보이지_않는다() throws Exception {
		MockMvc mvc = mockMvc();
		Long eventId = uploadEvent(mvc, "단추시험", "단추 시험 행사");

		mvc.perform(get("/gallery/events/{id}", eventId))
				.andExpect(content().string(Matchers.not(Matchers.containsString("class=\"edit-btn\""))));

		mvc.perform(get("/gallery/events/{id}", eventId).session(loginAsAdmin(mvc)))
				.andExpect(content().string(Matchers.containsString("class=\"edit-btn\"")));
	}

	@Test
	void 관리자는_행사_정보를_고친다() throws Exception {
		MockMvc mvc = mockMvc();
		Long eventId = uploadEvent(mvc, "수정시험", "고치기 전 제목");
		MockHttpSession admin = loginAsAdmin(mvc);

		mvc.perform(post("/admin/events/{id}/edit", eventId)
						.param("title", "고친 뒤 제목")
						.param("content", "고친 내용")
						.param("eventDate", LocalDate.now().minusDays(3).toString())
						.session(admin))
				.andExpect(redirectedUrl("/gallery/events/" + eventId));

		var saved = eventRepository.findById(eventId).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo("고친 뒤 제목");
		assertThat(saved.getContent()).isEqualTo("고친 내용");
		assertThat(saved.getEventDate()).isEqualTo(LocalDate.now().minusDays(3));
	}

	@Test
	void 관리자는_행사를_사진까지_지운다() throws Exception {
		MockMvc mvc = mockMvc();
		Long eventId = uploadEvent(mvc, "삭제시험", "지울 행사");
		long photosBefore = photoRepository.count();
		MockHttpSession admin = loginAsAdmin(mvc);

		mvc.perform(post("/admin/events/{id}/delete", eventId).session(admin))
				.andExpect(redirectedUrl("/gallery"));

		assertThat(eventRepository.findById(eventId)).isEmpty();
		// 딸린 사진 두 장도 함께 사라진다
		assertThat(photoRepository.count()).isEqualTo(photosBefore - 2);
	}

	@Test
	void 관리자는_사진_한_장만_뺄_수_있다() throws Exception {
		MockMvc mvc = mockMvc();
		Long eventId = uploadEvent(mvc, "한장시험", "한 장만 뺄 행사");
		MockHttpSession admin = loginAsAdmin(mvc);

		Long photoId = eventRepository.findWithPhotos(eventId).orElseThrow()
				.getPhotos().get(0).getId();

		mvc.perform(post("/admin/photos/{id}/delete", photoId).session(admin))
				.andExpect(redirectedUrl("/gallery/events/" + eventId));

		// 행사는 남고 사진만 한 장 줄어든다
		assertThat(eventRepository.findWithPhotos(eventId).orElseThrow().getPhotoCount()).isEqualTo(1);
	}

	@Test
	void 관리를_끝내면_권한이_사라진다() throws Exception {
		MockMvc mvc = mockMvc();
		MockHttpSession admin = loginAsAdmin(mvc);

		mvc.perform(post("/admin/logout").session(admin))
				.andExpect(redirectedUrl("/gallery"));

		mvc.perform(get("/admin").session(admin))
				.andExpect(redirectedUrl("/admin/login"));
	}

	@Test
	void 목록에서_행사_사진을_통째로_내려받는다() throws Exception {
		MockMvc mvc = mockMvc();
		Long eventId = uploadEvent(mvc, "내려받기시험", "내려받을 행사");

		// 목록에 내려받기 링크가 있다
		mvc.perform(get("/gallery"))
				.andExpect(content().string(Matchers.containsString(
						"/gallery/events/" + eventId + "/photos.zip")));

		// 눌렀을 때 실제로 압축 파일이 내려온다.
		// 압축은 흘려보내며 쓰므로(StreamingResponseBody) 다 쓰일 때까지 기다렸다가 받는다.
		var started = mvc.perform(get("/gallery/events/{id}/photos.zip", eventId))
				.andExpect(request().asyncStarted())
				.andReturn();

		byte[] zip = mvc.perform(asyncDispatch(started))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						Matchers.containsString("attachment")))
				.andReturn().getResponse().getContentAsByteArray();

		// 올린 사진 두 장이 들어 있다
		List<String> names = new ArrayList<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				names.add(entry.getName());
			}
		}
		assertThat(names).hasSize(2);
		assertThat(names.get(0)).startsWith("01_");
		assertThat(names.get(1)).startsWith("02_");
	}

	@Test
	void 사진_한_장만_내려받을_수도_있다() throws Exception {
		MockMvc mvc = mockMvc();
		Long eventId = uploadEvent(mvc, "한장내려받기", "한 장 내려받을 행사");
		Long photoId = eventRepository.findWithPhotos(eventId).orElseThrow()
				.getPhotos().get(0).getId();

		mvc.perform(get("/gallery/photos/{id}/file", photoId).param("download", "true"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						Matchers.containsString("attachment")));

		// download 를 주지 않으면 화면에 그대로 보여 준다 (목록의 그림이 이 주소를 쓴다)
		mvc.perform(get("/gallery/photos/{id}/file", photoId))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Content-Disposition"));
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
