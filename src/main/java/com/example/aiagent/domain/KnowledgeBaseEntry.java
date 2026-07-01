package com.example.aiagent.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity representing a chunk of documentation stored in the knowledge base.
 *
 * <p>The {@code embedding} field stores the OpenAI {@code text-embedding-3-small}
 * 1536-dimensional float vector as a raw {@code float[]} array, persisted into the
 * MySQL 9.0 native {@code VECTOR(1536)} column type via a custom
 * {@link FloatArrayVectorType} Hibernate UserType.</p>
 *
 * <p>Vector similarity search (cosine distance) is performed via a native SQL query
 * in {@link com.example.aiagent.repository.KnowledgeBaseRepository} using MySQL's
 * built-in {@code COSINE_DISTANCE()} function.</p>
 */
@Entity
@Table(name = "knowledge_base")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class KnowledgeBaseEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "content_chunk", nullable = false, columnDefinition = "TEXT")
    private String contentChunk;

    /**
     * Raw float array persisted as MySQL VECTOR(1536).
     *
     * <p>Hibernate does not have a built-in mapping for MySQL VECTOR columns.
     * We store it as a serializable byte array using {@code columnDefinition = "VECTOR(1536)"}
     * and handle the binary conversion in the repository layer via native SQL.</p>
     */
    @Column(name = "embedding", columnDefinition = "BLOB")
    private byte[] embedding;
}
