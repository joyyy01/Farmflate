package com.example.aiworkspace.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FreshCommunitySchemaMigrationTest {

    @Test
    void initial_community_comments_schema_contains_every_base_time_column() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V4__init_community_schema.sql")) {
            assertThat(input).as("V4 initial community schema").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            String commentsTable = sql.substring(sql.indexOf("create table if not exists community_comments"),
                    sql.indexOf("create index if not exists idx_community_posts_category"));

            assertThat(commentsTable).contains("created_at timestamp with time zone");
            assertThat(commentsTable).contains("updated_at timestamp with time zone");
        }
    }
}
