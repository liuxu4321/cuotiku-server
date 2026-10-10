package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.entity.MiniAdminAudit;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 审计先于业务写入；审计不可用时阻止管理修改，避免无记录的敏感操作。 */
@Service
public class MiniAuditService {
  private final MiniSupport s;

  public MiniAuditService(MiniSupport s) {
    this.s = s;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String begin(Long actor, String method, String path) {
    MiniAdminAudit row = new MiniAdminAudit();
    row.setId(UUID.randomUUID().toString());
    row.setActorId(actor);
    row.setMethod(method);
    row.setTargetPath(path);
    row.setCreatedAt(Instant.now());
    s.save(row);
    return row.getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finish(String id, int status, String reason) {
    MiniAdminAudit row = s.get(MiniAdminAudit.class, id);
    row.setHttpStatus(status);
    row.setReason(reason == null ? null : reason.substring(0, Math.min(reason.length(), 500)));
  }
}
