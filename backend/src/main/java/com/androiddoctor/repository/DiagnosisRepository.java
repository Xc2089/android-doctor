package com.androiddoctor.repository;

import com.androiddoctor.model.Diagnosis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, UUID> {
    List<Diagnosis> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
