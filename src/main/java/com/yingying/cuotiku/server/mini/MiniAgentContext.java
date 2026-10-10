package com.yingying.cuotiku.server.mini;

/** 将小程序调用归属传递给已有Agent审计，finally清理以避免线程复用串学生。 */
public final class MiniAgentContext {
  private MiniAgentContext() {}

  public record Scope(
      Long userId,
      String studentId,
      String entryId,
      String assetId,
      String revisionId,
      String inputHash) {}

  private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

  public static Scope current() {
    return CURRENT.get();
  }

  public static void set(Scope scope) {
    CURRENT.set(scope);
  }

  public static void clear() {
    CURRENT.remove();
  }
}
