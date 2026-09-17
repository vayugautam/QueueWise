package com.queuewise.counter.persistence;

import com.queuewise.counter.domain.CounterSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CounterSessionRepository extends JpaRepository<CounterSession, UUID> {
}
