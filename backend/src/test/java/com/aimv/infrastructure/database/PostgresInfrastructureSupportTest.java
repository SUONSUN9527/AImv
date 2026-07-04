package com.aimv.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

class PostgresInfrastructureSupportTest {

    @Test
    void createsPgSimpleDataSourceFromPostgresProfileProperties() {
        PostgresDataSourceConfiguration configuration = new PostgresDataSourceConfiguration();

        PGSimpleDataSource dataSource = (PGSimpleDataSource) configuration.dataSource(
            "jdbc:postgresql://127.0.0.1:5432/aimv", "aimv", "secret");

        assertThat(dataSource.getUrl()).startsWith("jdbc:postgresql://127.0.0.1:5432/aimv");
        assertThat(dataSource.getUser()).isEqualTo("aimv");
    }

    @Test
    void createsPgSimpleDataSourceWithBlankOptionalCredentials() {
        PostgresDataSourceConfiguration configuration = new PostgresDataSourceConfiguration();

        PGSimpleDataSource dataSource = (PGSimpleDataSource) configuration.dataSource(
            "jdbc:postgresql://127.0.0.1:5432/aimv", "", "");

        assertThat(dataSource.getUrl()).startsWith("jdbc:postgresql://127.0.0.1:5432/aimv");
        assertThat(dataSource.getUser()).isNull();
    }

    @Test
    void serializesAndParsesJsonbValuesWithoutMutableSharedState() throws Exception {
        Object jsonb = PostgresJsonSupport.jsonb(Map.of("stage", "I40", "ids", List.of("job-1")));

        assertThat(jsonb.toString()).contains("\"stage\":\"I40\"");
        assertThat(PostgresJsonSupport.stringList("[\"a\",\"b\"]")).containsExactly("a", "b");
        assertThat(PostgresJsonSupport.stringList("")).isEmpty();
        assertThat(PostgresJsonSupport.objectMap("{\"score\":95}")).containsEntry("score", 95);
        assertThat(PostgresJsonSupport.objectMap(null)).isEmpty();
    }

    @Test
    void rejectsMalformedJsonValues() {
        assertThatThrownBy(() -> PostgresJsonSupport.stringList("{"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JSONB 字符串列表解析失败");
        assertThatThrownBy(() -> PostgresJsonSupport.objectMap("{"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JSONB 对象解析失败");
    }
}
