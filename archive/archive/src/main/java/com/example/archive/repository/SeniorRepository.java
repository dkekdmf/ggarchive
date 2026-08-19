package com.example.archive.repository;

import com.example.archive.domain.Senior;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeniorRepository extends JpaRepository<Senior, Long> {

	Optional<Senior> findByName(String name);
}
