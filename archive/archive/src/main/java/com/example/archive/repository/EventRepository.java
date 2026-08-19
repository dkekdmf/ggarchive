package com.example.archive.repository;

import com.example.archive.domain.Event;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

	/**
	 * 직원용 목록. 검색어(제목 또는 보낸 분 이름)와 행사 날짜 범위는 모두 선택 사항이라 null 이면 조건에서 빠진다.
	 * 사진 목록은 화면에서 바로 쓰이므로 카드 하나당 쿼리가 또 나가지 않도록 따로 불러온다.
	 */
	@Query(value = """
			select e from Event e
			join fetch e.senior s
			where (:keyword is null or e.title like %:keyword% or s.name like %:keyword%)
			  and (:from is null or e.eventDate >= :from)
			  and (:to is null or e.eventDate <= :to)
			  and (:onlyUnchecked = false or e.checked = false)
			order by e.createdAt desc
			""",
			countQuery = """
					select count(e) from Event e
					join e.senior s
					where (:keyword is null or e.title like %:keyword% or s.name like %:keyword%)
					  and (:from is null or e.eventDate >= :from)
					  and (:to is null or e.eventDate <= :to)
					  and (:onlyUnchecked = false or e.checked = false)
					""")
	Page<Event> search(@Param("keyword") String keyword,
			@Param("from") LocalDate from,
			@Param("to") LocalDate to,
			@Param("onlyUnchecked") boolean onlyUnchecked,
			Pageable pageable);

	@Query("select e from Event e join fetch e.senior left join fetch e.photos where e.id = :id")
	Optional<Event> findWithPhotos(@Param("id") Long id);

	long countByCheckedFalse();

	long countByCreatedAtGreaterThanEqual(LocalDateTime from);
}
