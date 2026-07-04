package com.aimv.infrastructure.database;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Configuration
@Profile("postgres")
public class PostgresDataSourceConfiguration {

    @Bean
    public DataSource dataSource(@Value("${aimv.postgres.url}") String url,
            @Value("${aimv.postgres.username:}") String username,
            @Value("${aimv.postgres.password:}") String password) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        if (StringUtils.hasText(username)) {
            dataSource.setUser(username);
        }
        if (StringUtils.hasText(password)) {
            dataSource.setPassword(password);
        }
        return dataSource;
    }
}
