package com.yingying.cuotiku.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.MySQLContainer;

/** 真实旧表升级：不能只用空库建表验证生成列的依赖顺序。 */
class LegacySchemaUpgradeTest {
  @Test
  void addsScopeAfterStudentColumnAndPreservesExistingRows() throws Exception {
    try (var mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("legacy_upgrade")) {
      mysql.start();
      var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
      try (var connection = source.getConnection(); var sql = connection.createStatement()) {
        sql.execute("create table user_subject (id bigint not null auto_increment primary key, user_id bigint not null, name varchar(30) not null, normalized_name varchar(30) not null, system_key varchar(16), sort_order int not null, status varchar(10) not null, revision int not null, created_at datetime(6) not null, updated_at datetime(6) not null)");
        sql.execute("insert into user_subject(user_id,name,normalized_name,sort_order,status,revision,created_at,updated_at) values (7,'数学','数学',0,'ACTIVE',0,now(),now())");
      }
      for (int startup = 0; startup < 2; startup++) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setPackagesToScan("com.yingying.cuotiku.server.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.dialect", "com.yingying.cuotiku.server.config.DependencyOrderedMySqlDialect", "hibernate.hbm2ddl.auto", "update", "hibernate.hbm2ddl.halt_on_error", "true"));
        try {
          factory.afterPropertiesSet();
          try (var connection = source.getConnection(); var sql = connection.createStatement();
              var rows = sql.executeQuery("select name,student_id,scope_key from user_subject where id=1")) {
            assertTrue(rows.next()); assertEquals("数学", rows.getString(1));
            assertNull(rows.getString(2)); assertEquals("user:7", rows.getString(3));
            assertFalse(rows.next());
          }
          try (var connection = source.getConnection(); var sql = connection.createStatement();
              var indexes = sql.executeQuery("select count(distinct INDEX_NAME) from information_schema.STATISTICS where TABLE_SCHEMA=database() and TABLE_NAME='user_subject' and INDEX_NAME in ('uk_subject_scope_name','uk_subject_scope_system') and NON_UNIQUE=0")) {
            assertTrue(indexes.next()); assertEquals(2, indexes.getInt(1));
          }
        } finally { factory.destroy(); }
      }
    }
  }
}
