package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.service.AgentRuntimeService;
import com.yingying.cuotiku.server.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

/** 讲解和举一反三共用已有模型配置，缓存键包含账号、学生、题图和分类上下文。 */
@Service
public class MiniAgentService {
  private final MiniAgentStore store;
  private final AgentRuntimeService runtime;

  public MiniAgentService(MiniAgentStore store, AgentRuntimeService runtime) {
    this.store = store;
    this.runtime = runtime;
  }

  public Object run(User user, String student, String entryId, String type, JsonNode body) {
    if (!user.isAiEnabled()) throw ApiException.forbidden("账号未开通AI权限");
    boolean force = bool(body, "forceRefresh", false);
    MiniAgentStore.Input input = store.input(user, student, entryId, type);
    MiniAgentContext.set(
        new MiniAgentContext.Scope(
            user.getId(),
            student,
            entryId,
            input.assetId(),
            input.revisionId(),
            input.contentHash()));
    try {
      AgentRuntimeService.AgentRawResult result =
          runtime.run(
              type,
              input.vars(),
              input.bytes(),
              MimeTypeUtils.parseMimeType(input.mime()),
              user,
              input.key(),
              force);
      return store.result(user, student, entryId, type, input, result);
    } finally {
      MiniAgentContext.clear();
    }
  }
}
