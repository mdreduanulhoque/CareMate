package com.carebridge.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = "sender")
    Page<ChatMessage> findByRoomIdOrderBySentAtDescIdDesc(String roomId, Pageable pageable);

    @Query("""
            SELECT m FROM ChatMessage m
            JOIN FETCH m.sender
            WHERE m.id IN (
                SELECT MAX(m2.id) FROM ChatMessage m2
                WHERE m2.roomId IN :roomIds
                GROUP BY m2.roomId
            )
            """)
    List<ChatMessage> findLatestMessagesByRoomIds(@Param("roomIds") Collection<String> roomIds);
}
