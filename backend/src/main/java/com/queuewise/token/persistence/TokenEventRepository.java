package com.queuewise.token.persistence;

import com.queuewise.token.domain.TokenEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenEventRepository extends JpaRepository<TokenEvent, Long> {
    List<TokenEvent> findAllByTokenIdOrderByCreatedAtAsc(UUID tokenId);
}
