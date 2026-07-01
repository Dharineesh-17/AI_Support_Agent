package com.example.aiagent.repository;

import com.example.aiagent.domain.KnowledgeBaseEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link KnowledgeBaseEntry} entities.
 *
 * <p>Extends {@link JpaRepository} for standard CRUD and provides a custom
 * native vector similarity search via {@link KnowledgeBaseRepositoryCustom}.</p>
 *
 * <p>The cosine-distance query leverages MySQL 9.0's native
 * {@code COSINE_DISTANCE()} function which operates directly on the
 * {@code VECTOR(1536)} column. This avoids any application-side computation
 * and allows the database engine to use its VECTOR INDEX for sub-millisecond
 * approximate nearest-neighbour retrieval at scale.</p>
 */
@Repository
public interface KnowledgeBaseRepository
        extends JpaRepository<KnowledgeBaseEntry, Long>, KnowledgeBaseRepositoryCustom {

    /**
     * Finds knowledge base entries whose content contains the given keyword,
     * useful for fallback text-based search when the embedding model is unavailable.
     *
     * @param keyword the search term to look for (LIKE wildcard added by the query)
     * @return list of matching entries
     */
    List<KnowledgeBaseEntry> findByContentChunkContainingIgnoreCase(String keyword);
}
