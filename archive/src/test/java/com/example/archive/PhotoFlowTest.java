package com.example.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.archive.repository.EventRepository;
import com.example.archive.repository.PhotoRepository;
import com.example.archive.repository.SeniorRepository;
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
	private SeniorRepository seniorRepository;

	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).build();
	}

	private MockMultipartFile jpeg(String filename) {
		return new MockMultipartFile("photos", filename, MediaType.IMAGE_JPEG_VALUE, "fake-bytes".getBytes());
	}

	/** 필수 항목을 모두 채운 업로드 요청. 날짜는 오늘로 둔다. */
	private MockMultipartHttpServletRequestBuilder submit(String title) {
		return submit(title, LocalDate.now().toString());
	}

	/** 날짜까지 정해서 보내는 경우. 같은 이름의 값이 두 번 들어가지 않게 여기서 한 번만 넣는다. */
	private MockMultipartHttpServletRequestBuilder submit(String title, String date) {
		MockMultipartHttpServletRequestBuilder request = multipart("/upload");
		request.file(jpeg("a.jpg"));
		request.param("title", title);
		request.param("company", "○○이벤트");
		request.param("place", "2층 강당");
		request.param("purpose", "생신 축하 모임");
		request.param("eventDate", date);
		return request;
	}

	private Long idOf(String title) {
		return eventRepository.findAll().stream()
				.filter(e -> e.getTitle().equals(title))
				.findFirst().orElseThrow().getId();
	}

	private MockHttpSession loginAsAdmin(MockMvc mvc) throws Exception {
		// 컨트롤러가 폼 형식만 받게 되어 있어(consumes) 실제 브라우저처럼 형식을 밝혀 준다
		return (MockHttpSession) mvc.perform(post("/admin/login")
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("password", "test1234"))
				.andExpect(redirectedUrl("/gallery"))
				.andReturn().getRequest().getSession();
	}

	// ── 사진을 올리는 흐름 ─────────────────────────────

	@Test
	void 첫_화면은_달력이고_사진_보내기로_넘어간다() throws Exception {
		MockMvc mvc = mockMvc();

		mvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("행사 달력")));

		mvc.perform(get("/upload"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("어떤 행사인가요?")))
				.andExpect(content().string(Matchers.containsString("업체명")))
				.andExpect(content().string(Matchers.containsString("사용 장소")))
				.andExpect(content().string(Matchers.containsString("사용 목적")))
				.andExpect(content().string(Matchers.containsString("사용 날짜")))
				.andExpect(content().string(Matchers.containsString("최소 3장 이상 필수입니다")))
				// 이름 칸은 없앴다
				.andExpect(content().string(Matchers.not(Matchers.containsString("seniorName"))));
	}

	@Test
	void 행사정보와_사진을_함께_보낸다() throws Exception {
		MockMvc mvc = mockMvc();
		long beforeEvents = eventRepository.count();
		long beforePhotos = photoRepository.count();

		mvc.perform(submit("경로당 생신잔치")
						.file(jpeg("b.jpg"))
						.param("content", "스무 분이 모였습니다"))
				.andExpect(redirectedUrl("/done"));

		assertThat(eventRepository.count()).isEqualTo(beforeEvents + 1);
		assertThat(photoRepository.count()).isEqualTo(beforePhotos + 2);

		var saved = eventRepository.findById(idOf("경로당 생신잔치")).orElseThrow();
		assertThat(saved.getPlace()).isEqualTo("2층 강당");
		assertThat(saved.getPurpose()).isEqualTo("생신 축하 모임");
		assertThat(saved.getUsageEnd()).isNull();
	}

	@Test
	void 하루짜리_행사는_종료일이_비어_있다() throws Exception {
		mockMvc().perform(submit("하루만 쓰는 행사"))
				.andExpect(redirectedUrl("/done"));

		var saved = eventRepository.findById(idOf("하루만 쓰는 행사")).orElseThrow();
		assertThat(saved.getUsageEnd()).isNull();
		assertThat(saved.isMultiDay()).isFalse();
	}

	@Test
	void 필수_항목이_빠지면_되돌려_보낸다() throws Exception {
		MockMvc mvc = mockMvc();
		long before = eventRepository.count();

		mvc.perform(multipart("/upload").file(jpeg("a.jpg"))
						.param("title","장소 없는 행사")
						.param("purpose", "모임").param("eventDate", LocalDate.now().toString()))
				.andExpect(redirectedUrl("/upload"));

		mvc.perform(multipart("/upload").file(jpeg("a.jpg"))
						.param("title","목적 없는 행사")
						.param("company", "○○이벤트").param("place", "강당").param("eventDate", LocalDate.now().toString()))
				.andExpect(redirectedUrl("/upload"));

		assertThat(eventRepository.count()).isEqualTo(before);
	}

	@Test
	void 이름은_받지_않는다() throws Exception {
		MockMvc mvc = mockMvc();
		long before = eventRepository.count();

		// 옛 화면이 이름을 딸려 보내더라도 그냥 무시하고 저장한다
		mvc.perform(multipart("/upload").file(jpeg("a.jpg"))
						.param("seniorName", "김순자")
						.param("title", "이름 없이 보낸 행사").param("company", "○○이벤트").param("place", "강당")
						.param("purpose", "모임").param("eventDate", LocalDate.now().toString()))
				.andExpect(redirectedUrl("/done"));

		assertThat(eventRepository.count()).isEqualTo(before + 1);

		// 이름은 어디에도 남지 않는다. 모든 행사는 자리표 하나에 매달린다.
		// (관계를 타면 세션이 없어 읽을 수 없으므로 저장소에서 직접 확인한다)
		assertThat(seniorRepository.findByName("김순자")).isEmpty();
		assertThat(seniorRepository.findByName("이름 없음")).isPresent();
	}

	@Test
	void 날짜를_여러_형태로_적어도_읽는다() throws Exception {
		MockMvc mvc = mockMvc();

		String[] forms = {"2026-08-11", "2026.08.12", "2026/8/13", "20260814"};
		String[] titles = {"형태1", "형태2", "형태3", "형태4"};
		java.time.LocalDate[] expected = {
				java.time.LocalDate.of(2026, 8, 11), java.time.LocalDate.of(2026, 8, 12),
				java.time.LocalDate.of(2026, 8, 13), java.time.LocalDate.of(2026, 8, 14)};

		for (int i = 0; i < forms.length; i++) {
			mvc.perform(submit(titles[i], forms[i]))
					.andExpect(redirectedUrl("/done"));
			assertThat(eventRepository.findById(idOf(titles[i])).orElseThrow().getEventDate())
					.isEqualTo(expected[i]);
		}
	}

	@Test
	void 앞으로_있을_행사도_적을_수_있다() throws Exception {
		MockMvc mvc = mockMvc();
		LocalDate future = LocalDate.now().plusDays(30);

		mvc.perform(submit("다음 달 행사", future.toString()))
				.andExpect(redirectedUrl("/done"));

		assertThat(eventRepository.findById(idOf("다음 달 행사")).orElseThrow().getEventDate())
				.isEqualTo(future);
	}

	@Test
	void 날짜가_엉뚱하면_되돌려_보낸다() throws Exception {
		long before = eventRepository.count();

		mockMvc().perform(submit("엉뚱한 날짜", "아무거나"))
				.andExpect(redirectedUrl("/upload"));

		assertThat(eventRepository.count()).isEqualTo(before);
	}

	@Test
	void 종료일이_시작일보다_앞서면_거절한다() throws Exception {
		long before = eventRepository.count();

		mockMvc().perform(submit("거꾸로 된 기간")
						.param("usageEnd", LocalDate.now().minusDays(3).toString()))
				.andExpect(redirectedUrl("/upload"));

		assertThat(eventRepository.count()).isEqualTo(before);
	}

	@Test
	void 사진_없이_일정만_먼저_저장할_수_있다() throws Exception {
		MockMvc mvc = mockMvc();
		long before = eventRepository.count();

		mvc.perform(multipart("/upload")
						.file(new MockMultipartFile("photos", "", MediaType.IMAGE_JPEG_VALUE, new byte[0]))
						.param("title", "사진 없는 행사")
						.param("company", "○○이벤트").param("place", "강당").param("purpose", "모임")
						.param("eventDate", LocalDate.now().toString()))
				.andExpect(redirectedUrl("/done"));

		assertThat(eventRepository.count()).isEqualTo(before + 1);
		Long id = idOf("사진 없는 행사");
		assertThat(eventRepository.findWithPhotos(id).orElseThrow().getPhotoCount()).isZero();

		// 달력과 목록에도 보인다
		mvc.perform(get("/")).andExpect(content().string(Matchers.containsString("사진 없는 행사")));
		mvc.perform(get("/gallery")).andExpect(content().string(Matchers.containsString("사진 없음")));

		// 나중에 사진을 넣으면 된다
		mvc.perform(multipart("/admin/events/{id}/photos", id)
						.file(jpeg("later.jpg"))
						.session(loginAsAdmin(mvc)))
				.andExpect(status().is3xxRedirection());

		assertThat(eventRepository.findWithPhotos(id).orElseThrow().getPhotoCount()).isEqualTo(1);
	}

	@Test
	void 정해진_장수보다_많이_보내면_한_장도_저장하지_않는다() throws Exception {
		long beforeEvents = eventRepository.count();
		long beforePhotos = photoRepository.count();

		MockMultipartHttpServletRequestBuilder request = submit("너무 많은 사진");
		for (int i = 0; i < 11; i++) {
			request.file(jpeg(i + ".jpg"));
		}

		mockMvc().perform(request).andExpect(redirectedUrl("/upload"));

		assertThat(eventRepository.count()).isEqualTo(beforeEvents);
		assertThat(photoRepository.count()).isEqualTo(beforePhotos);
	}

	@Test
	void 여러_번_보내도_자리표는_하나만_생긴다() throws Exception {
		MockMvc mvc = mockMvc();

		mvc.perform(submit("첫 번째 행사")).andExpect(redirectedUrl("/done"));
		long after1 = seniorRepository.count();

		mvc.perform(submit("두 번째 행사")).andExpect(redirectedUrl("/done"));

		assertThat(seniorRepository.count()).isEqualTo(after1);
	}

	// ── 보는 화면 ─────────────────────────────────────

	@Test
	void 목록은_비밀번호_없이_누구나_보고_업체명과_장소가_나온다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("가을 나들이")).andExpect(redirectedUrl("/done"));

		mvc.perform(get("/gallery"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("가을 나들이")))
				.andExpect(content().string(Matchers.containsString("○○이벤트")))
				.andExpect(content().string(Matchers.containsString("2층 강당")));
	}

	@Test
	void 상세_화면에_사용정보가_모두_나온다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("상세 화면 시험")
						.param("content", "자세한 내용은 여기에서만 보인다"))
				.andExpect(redirectedUrl("/done"));

		Long id = idOf("상세 화면 시험");

		mvc.perform(get("/gallery"))
				.andExpect(content().string(Matchers.containsString("/gallery/events/" + id)))
				.andExpect(content().string(Matchers.not(
						Matchers.containsString("자세한 내용은 여기에서만 보인다"))));

		mvc.perform(get("/gallery/events/{id}", id))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("상세 화면 시험")))
				.andExpect(content().string(Matchers.containsString("업체명")))
				.andExpect(content().string(Matchers.containsString("사용 장소")))
				.andExpect(content().string(Matchers.containsString("사용 목적")))
				.andExpect(content().string(Matchers.containsString("생신 축하 모임")))
				.andExpect(content().string(Matchers.containsString("자세한 내용은 여기에서만 보인다")));
	}

	@Test
	void 사진_파일도_비밀번호_없이_열린다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("사진 열람 시험")).andExpect(redirectedUrl("/done"));

		Long photoId = photoRepository.findAll().stream()
				.map(p -> p.getId()).max(Long::compareTo).orElseThrow();

		mvc.perform(get("/gallery/photos/{id}/file", photoId)).andExpect(status().isOk());
	}

	@Test
	void 달력에서_그_달의_행사를_본다() throws Exception {
		MockMvc mvc = mockMvc();
		LocalDate day = LocalDate.now().withDayOfMonth(10);

		mvc.perform(submit("달력 시험 행사", day.toString()))
				.andExpect(redirectedUrl("/done"));

		mvc.perform(get("/gallery/calendar")
						.param("year", String.valueOf(day.getYear()))
						.param("month", String.valueOf(day.getMonthValue())))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("달력 시험 행사")));

		LocalDate next = day.plusMonths(1);
		mvc.perform(get("/gallery/calendar")
						.param("year", String.valueOf(next.getYear()))
						.param("month", String.valueOf(next.getMonthValue())))
				.andExpect(content().string(Matchers.not(
						Matchers.containsString("달력 시험 행사"))));
	}

	// ── 내려받기 ──────────────────────────────────────

	@Test
	void 행사_사진을_통째로_내려받는다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("내려받을 행사").file(jpeg("b.jpg")))
				.andExpect(redirectedUrl("/done"));

		Long id = idOf("내려받을 행사");

		mvc.perform(get("/gallery"))
				.andExpect(content().string(Matchers.containsString(
						"/gallery/events/" + id + "/photos.zip")));

		var started = mvc.perform(get("/gallery/events/{id}/photos.zip", id))
				.andExpect(request().asyncStarted())
				.andReturn();

		byte[] zip = mvc.perform(asyncDispatch(started))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")))
				.andReturn().getResponse().getContentAsByteArray();

		List<String> names = new ArrayList<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				names.add(entry.getName());
			}
		}
		assertThat(names).hasSize(2);
		assertThat(names.get(0)).startsWith("01_");
	}

	// ── 관리자 ────────────────────────────────────────

	@Test
	void 로그인하지_않으면_고치기_지우기를_할_수_없다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("권한 시험 행사")).andExpect(redirectedUrl("/done"));
		Long id = idOf("권한 시험 행사");

		mvc.perform(get("/admin")).andExpect(redirectedUrl("/admin/login"));
		mvc.perform(get("/admin/events/{id}/edit", id)).andExpect(redirectedUrl("/admin/login"));
		mvc.perform(post("/admin/events/{id}/delete", id)).andExpect(redirectedUrl("/admin/login"));
		mvc.perform(post("/admin/events/{id}/field", id)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("field", "title").param("value", "몰래 고침"))
				.andExpect(redirectedUrl("/admin/login"));

		assertThat(eventRepository.findById(id).orElseThrow().getTitle()).isEqualTo("권한 시험 행사");
	}

	@Test
	void 관리자는_항목_하나만_골라_고친다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("원래 제목")).andExpect(redirectedUrl("/done"));
		Long id = idOf("원래 제목");
		MockHttpSession admin = loginAsAdmin(mvc);

		mvc.perform(post("/admin/events/{id}/field", id)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("field", "place").param("value", "1층 회의실").session(admin))
				.andExpect(status().is3xxRedirection());

		mvc.perform(post("/admin/events/{id}/field", id)
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("field", "purpose").param("value", "건강 강좌").session(admin))
				.andExpect(status().is3xxRedirection());

		var saved = eventRepository.findById(id).orElseThrow();
		assertThat(saved.getPlace()).isEqualTo("1층 회의실");
		assertThat(saved.getPurpose()).isEqualTo("건강 강좌");
		assertThat(saved.getTitle()).isEqualTo("원래 제목");
	}

	@Test
	void 관리자는_나중에_사진을_더_넣는다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("사진 더 넣을 행사")).andExpect(redirectedUrl("/done"));
		Long id = idOf("사진 더 넣을 행사");
		MockHttpSession admin = loginAsAdmin(mvc);

		mvc.perform(multipart("/admin/events/{id}/photos", id)
						.file(jpeg("more1.jpg"))
						.file(jpeg("more2.jpg"))
						.session(admin))
				.andExpect(status().is3xxRedirection());

		assertThat(eventRepository.findWithPhotos(id).orElseThrow().getPhotoCount()).isEqualTo(3);
	}

	@Test
	void 사진_추가도_로그인해야_한다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("권한 없는 추가")).andExpect(redirectedUrl("/done"));
		Long id = idOf("권한 없는 추가");

		mvc.perform(multipart("/admin/events/{id}/photos", id).file(jpeg("x.jpg")))
				.andExpect(redirectedUrl("/admin/login"));

		assertThat(eventRepository.findWithPhotos(id).orElseThrow().getPhotoCount()).isEqualTo(1);
	}

	@Test
	void 한_행사에_넣을_수_있는_장수를_넘기면_거절한다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("장수 초과 시험")).andExpect(redirectedUrl("/done"));
		Long id = idOf("장수 초과 시험");
		MockHttpSession admin = loginAsAdmin(mvc);

		var request = multipart("/admin/events/{id}/photos", id).session(admin);
		for (int i = 0; i < 10; i++) {
			request.file(jpeg(i + ".jpg"));
		}
		mvc.perform(request).andExpect(status().is3xxRedirection());

		// 한 장도 들어가지 않아야 한다
		assertThat(eventRepository.findWithPhotos(id).orElseThrow().getPhotoCount()).isEqualTo(1);
	}

	@Test
	void 관리자는_행사를_사진까지_지운다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("지울 행사").file(jpeg("b.jpg")))
				.andExpect(redirectedUrl("/done"));

		Long id = idOf("지울 행사");
		long photosBefore = photoRepository.count();

		mvc.perform(post("/admin/events/{id}/delete", id).session(loginAsAdmin(mvc)))
				.andExpect(redirectedUrl("/gallery"));

		assertThat(eventRepository.findById(id)).isEmpty();
		assertThat(photoRepository.count()).isEqualTo(photosBefore - 2);
	}

	@Test
	void 행사_단위로_확인_처리한다() throws Exception {
		MockMvc mvc = mockMvc();
		mvc.perform(submit("확인 처리 시험")).andExpect(redirectedUrl("/done"));
		Long id = idOf("확인 처리 시험");

		mvc.perform(post("/gallery/events/{id}/check", id))
				.andExpect(redirectedUrl("/gallery"));

		assertThat(eventRepository.findById(id).orElseThrow().isChecked()).isTrue();
	}
}
