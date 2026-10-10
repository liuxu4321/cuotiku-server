package com.yingying.cuotiku.server.config;

import java.util.Arrays;
import java.util.ArrayList;
import org.hibernate.boot.model.naming.Identifier;
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
 * 旧账号科目唯一索引替换为学生范围索引；新表创建和类型映射沿用 MySQLDialect。
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
        var commands = new ArrayList<>(Arrays.stream(super.getSqlAlterStrings(table, metadata, tableInfo, context))
            .sorted(Comparator.comparing(sql -> sql.toLowerCase(Locale.ROOT).contains("generated always as")))
            .toList());
        if (table.getName().equals("user_subject")
            && (tableInfo.getIndex(Identifier.toIdentifier("uk_user_norm_name")) != null
                || tableInfo.getIndex(Identifier.toIdentifier("uk_user_system_key")) != null)) {
          String target = context.format(tableInfo.getName());
          // 先建立学生范围约束，再移除历史账号范围约束；任何 DDL 失败即阻断启动。
          if (tableInfo.getIndex(Identifier.toIdentifier("uk_subject_scope_name")) == null)
            commands.add("alter table " + target + " add constraint uk_subject_scope_name unique (scope_key, normalized_name)");
          if (tableInfo.getIndex(Identifier.toIdentifier("uk_subject_scope_system")) == null)
            commands.add("alter table " + target + " add constraint uk_subject_scope_system unique (scope_key, system_key)");
          for (String old : new String[]{"uk_user_norm_name", "uk_user_system_key"})
            if (tableInfo.getIndex(Identifier.toIdentifier(old)) != null)
              commands.add("alter table " + target + " drop index " + old);
        }
        return commands.toArray(String[]::new);
      }
    };
  }
}
