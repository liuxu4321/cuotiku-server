package com.yingying.cuotiku.server.config;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.extract.spi.TableInformation;
import org.hibernate.tool.schema.internal.StandardTableMigrator;
import org.hibernate.tool.schema.internal.TableMigrator;

/**
 * 保留 JPA ddl-auto=update；旧表加列时先创建普通列，再创建依赖它们的生成列。
 * Hibernate 默认字段顺序可能把 scope_key 放在 student_id 前，MySQL 会拒绝该 DDL。
 * 新表创建、类型映射及索引规则仍使用 MySQLDialect 原有实现。
 */
public class DependencyOrderedMySqlDialect extends MySQLDialect {
  public DependencyOrderedMySqlDialect() { super(); }

  public DependencyOrderedMySqlDialect(DialectResolutionInfo info) { super(info); }

  @Override
  public TableMigrator getTableMigrator() {
    return new StandardTableMigrator(this) {
      @Override
      public String[] getSqlAlterStrings(Table table, Metadata metadata,
          TableInformation tableInfo, SqlStringGenerationContext context) {
        // 稳定排序：普通列原顺序不变，三个已定义生成列均仅依赖普通列。
        return Arrays.stream(super.getSqlAlterStrings(table, metadata, tableInfo, context))
            .sorted(Comparator.comparing(sql -> sql.toLowerCase(Locale.ROOT).contains("generated always as")))
            .toArray(String[]::new);
      }
    };
  }
}
