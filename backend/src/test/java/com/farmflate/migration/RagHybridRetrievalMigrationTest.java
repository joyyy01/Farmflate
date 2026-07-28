package com.farmflate.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RagHybridRetrievalMigrationTest {

    @Test
    void semantic_schema_is_skipped_when_pgvector_is_not_already_installed() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V17__rag_hybrid_retrieval.sql")) {
            assertThat(input).as("V17 hybrid retrieval migration").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            String extensionCheck = "if exists (select 1 from pg_extension where extname = 'vector') then";
            assertThat(sql).doesNotContain("create extension");
            assertThat(sql).contains("do $$", extensionCheck, "embedding vector(1536)", "using hnsw");
            assertThat(sql.indexOf(extensionCheck)).isLessThan(sql.indexOf("embedding vector(1536)"));
        }
    }
}
