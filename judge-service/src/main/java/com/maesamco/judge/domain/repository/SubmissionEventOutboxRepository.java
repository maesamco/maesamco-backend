package com.maesamco.judge.domain.repository;

import com.maesamco.judge.domain.entity.SubmissionEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubmissionEventOutboxRepository extends JpaRepository<SubmissionEventOutbox, UUID> {
}
