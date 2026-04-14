package com.distributed.sharding.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class ShardDataSourceConfig {

    @Bean
    public DataSource shard0DataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:shard0;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        ds.setPoolName("shard0-pool");
        return ds;
    }

    @Bean
    public DataSource shard1DataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:shard1;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        ds.setPoolName("shard1-pool");
        return ds;
    }

    @Bean
    public DataSource shard2DataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:shard2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setDriverClassName("org.h2.Driver");
        ds.setPoolName("shard2-pool");
        return ds;
    }
}
