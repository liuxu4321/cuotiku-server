package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.yingying.cuotiku.server.entity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 独立事务保存调用日志，业务失败回滚也不会丢失上游调用证据。 */
@Service
public class MiniAiAuditService {
  private final MiniSupport s;

  public MiniAiAuditService(MiniSupport s) {
    this.s = s;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void ocr(
      User user,
      String student,
      String itemId,
      String assetId,
      String action,
      int attempt,
      String trace,
      String requestId,
      int inputBytes,
      int outputBytes,
      long duration,
      Exception error,
      Object parameters) {
    AiCallLog log = new AiCallLog();
    log.setUserId(user.getId());
    log.setPhone(user.getPhone());
    log.setStudentId(student);
    log.setAiType(action);
    log.setProvider("TENCENT_OCR");
    log.setApiAction(action);
    log.setApiVersion("2018-11-19");
    log.setJobItemId(itemId);
    log.setInputAssetId(assetId);
    log.setAttemptNo(attempt);
    log.setTraceId(trace);
    log.setRequestId(requestId);
    log.setInputBytes(inputBytes);
    log.setOutputBytes(outputBytes);
    log.setDurationMs(duration);
    log.setSuccess(error == null);
    log.setStatus(error == null ? "SUCCEEDED" : "FAILED");
    log.setConfigSnapshotJson(s.encode(parameters));
    if (error != null) {
      log.setErrorCode(
          error instanceof com.yingying.cuotiku.server.web.ApiException e ? e.getCode() : 502);
      log.setErrorMessage("腾讯云调用失败，请使用traceId排查");
    }
    s.save(log);
  }
}
