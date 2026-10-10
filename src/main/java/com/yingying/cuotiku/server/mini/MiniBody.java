package com.yingying.cuotiku.server.mini;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** 保留PATCH的缺失/null语义；字段范围和跨字段约束由业务Service校验。 */
public final class MiniBody {
  @NotNull(message = "请求体不能为空")
  private final JsonNode payload;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public MiniBody(JsonNode payload) {
    this.payload = payload;
  }

  @AssertTrue(message = "请求体必须是JSON对象")
  public boolean isObject() {
    return payload != null && payload.isObject();
  }

  @JsonValue
  public JsonNode payload() {
    return payload;
  }
}
