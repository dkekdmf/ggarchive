package com.example.archive.repository;

import com.example.archive.domain.Photo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

	/**
	 * 내려받을 때 파일 이름에 보낸 분과 행사를 쓰므로 함께 읽어 둔다.
	 * (open-in-view 를 꺼 두어서 화면에서 뒤늦게 읽을 수 없다)
	 */
	@Query("select p from Photo p join fetch p.event e join fetch e.senior where p.id = :id")
	Optional<Photo> findWithEvent(@Param("id") Long id);
}
