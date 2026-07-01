package com.example.aiagent.repository;

import java.util.List;

/**
 * Custom repository interface for vector similarity search operations
 * against the {@code knowledge_base} table using MySQL 9.0 native VECTOR support.
 *
 * <p>Implemented by {@link KnowledgeBaseRepositoryImpl} which executes a
 * native SQL query using {@code COSINE_DISTANCE()} — MySQL's built-in function
 * for computing cosine distance between VECTOR columns.</p>
 */
public interface KnowledgeBaseRepositoryCustom {

    /**
     * Finds the top {@code limit} knowledge base content chunks whose stored
     * embedding is closest (smallest cosine distance) to the provided query embedding.
     *
     * <p>Executes the following native SQL:
     * <pre>{@code
     * SELECT content_chunk
     * FROM knowledge_base
     * WHERE embedding IS NOT NULL
     * ORDER BY COSINE_DISTANCE(embedding, STRING_TO_VECTOR(:userEmbedding))
     * LIMIT :limit
     * }</pre>
     *
     * <p>The {@code userEmbedding} is a comma-separated string representation of
     * the float[] vector (e.g. {@code "0.012,-0.034,0.781,..."}), which MySQL's
     * {@code STRING_TO_VECTOR()} converts to its internal VECTOR binary format.</p>
     *
     * @param userEmbedding a comma-separated float vector string for the query
     * @param limit         maximum number of chunks to return
     * @return list of content chunk strings ordered by ascending cosine distance
     */
    List<String> findTopSimilarChunks(String userEmbedding, int limit);
}
