package com.example.aiworkspace.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RagKnowledgeSchemaMigrationTest {

    @Test
    void ragSchemaDeclaresVectorProvenanceAndHybridIndexes() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V16__rag_knowledge_schema.sql"));

        assertThat(sql).contains("CREATE EXTENSION IF NOT EXISTS vector");
        assertThat(sql).contains("CREATE SCHEMA IF NOT EXISTS rag");
        assertThat(sql).contains("embedding vector(1536)");
        assertThat(sql).contains("USING hnsw (embedding vector_cosine_ops)");
        assertThat(sql).contains("USING gin (search_vector)");
        assertThat(sql).contains("content_sha256");
    }
}
