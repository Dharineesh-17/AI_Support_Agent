package com.example.aiagent.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Concrete implementation of {@link KnowledgeBaseRepositoryCustom} that executes
 * a MySQL 9.0 native vector cosine-similarity query.
 *
 * <p>MySQL 9.0 introduced the {@code VECTOR} data type and companion functions:
 * <ul>
 *   <li>{@code STRING_TO_VECTOR(str)} — converts a comma-separated float string to a VECTOR</li>
 *   <li>{@code COSINE_DISTANCE(v1, v2)} — returns the cosine distance (0 = identical, 1 = orthogonal)</li>
 * </ul>
 * The VECTOR INDEX on the {@code embedding} column allows approximate nearest-neighbour (ANN)
 * retrieval without a full table scan.</p>
 *
 * <p>Spring Data requires the implementing class to be named {@code <RepositoryName>Impl}
 * for automatic wiring via the custom repository fragment pattern.</p>
 */
@Slf4j
@Component
public class KnowledgeBaseRepositoryImpl implements KnowledgeBaseRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Native SQL that:
     * <ol>
     *   <li>Filters out rows without an embedding ({@code WHERE embedding IS NOT NULL})</li>
     *   <li>Converts the input comma-separated float string to a MySQL VECTOR via {@code STRING_TO_VECTOR}</li>
     *   <li>Orders rows by ascending cosine distance (most similar first)</li>
     *   <li>Returns only the top {@code :limit} rows</li>
     * </ol>
     */
    private static final String COSINE_SIMILARITY_QUERY =
            "SELECT content_chunk " +
            "FROM knowledge_base " +
            "WHERE embedding IS NOT NULL " +
            "ORDER BY COSINE_DISTANCE(embedding, STRING_TO_VECTOR(:userEmbedding)) " +
            "LIMIT :limit";

    /**
     * {@inheritDoc}
     *
     * <p>If the database query fails (e.g. during local development with an empty
     * knowledge base or a non-MySQL 9.0 instance), the exception is caught, logged,
     * and an empty list is returned so the calling service can gracefully degrade.</p>
     *
     * @param userEmbedding comma-separated float representation, e.g. {@code "0.01,-0.23,0.87,..."}
     * @param limit         maximum number of chunks to retrieve
     * @return list of content chunks ordered by cosine similarity, or empty list on error
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> findTopSimilarChunks(String userEmbedding, int limit) {
        if (userEmbedding == null || userEmbedding.isBlank()) {
            log.warn("findTopSimilarChunks called with null or blank embedding — returning empty list");
            return Collections.emptyList();
        }

        try {
            Query nativeQuery = entityManager.createNativeQuery(COSINE_SIMILARITY_QUERY);
            nativeQuery.setParameter("userEmbedding", userEmbedding);
            nativeQuery.setParameter("limit", limit);

            List<Object> results = nativeQuery.getResultList();

            List<String> chunks = results.stream()
                    .filter(r -> r instanceof String)
                    .map(r -> (String) r)
                    .toList();

            log.debug("Vector similarity search returned {} chunks (limit={})", chunks.size(), limit);
            return chunks;

        } catch (Exception ex) {
            log.error("Vector similarity search failed — falling back to empty context. " +
                      "Ensure MySQL 9.0+ is running and the embedding column is populated. " +
                      "Error: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}
