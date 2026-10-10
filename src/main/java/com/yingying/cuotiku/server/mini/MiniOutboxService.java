package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.yingying.cuotiku.server.entity.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 任务与消息同事务落库；数据库租约让重启后可恢复未完成任务。 */
@Service
@Transactional
public class MiniOutboxService {
  private final MiniSupport s;

  public MiniOutboxService(MiniSupport s) {
    this.s = s;
  }

  public void enqueue(String type, String id, String event) {
    OutboxEvent row = new OutboxEvent();
    row.setAggregateType(type);
    row.setAggregateId(id);
    row.setEventType(event);
    row.setPayloadJson(s.encode(map("id", id)));
    row.setNextAttemptAt(Instant.now());
    s.save(row);
  }

  public record Work(String eventId, String type, String id, int attempt) {}

  public Work claim() {
    List<OutboxEvent> rows =
        s.em
            .createQuery(
                "select e from OutboxEvent e where (e.status='PENDING' or e.status='PROCESSING')"
                    + " and (e.nextAttemptAt is null or e.nextAttemptAt<=:now) order by"
                    + " e.createdAt,e.id",
                OutboxEvent.class)
            .setParameter("now", Instant.now())
            .setMaxResults(1)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    if (rows.isEmpty()) return null;
    OutboxEvent row = rows.get(0);
    row.setStatus("PROCESSING");
    row.setAttemptCount(row.getAttemptCount() + 1);
    row.setNextAttemptAt(Instant.now().plusSeconds(600));
    return new Work(
        row.getId(), row.getAggregateType(), row.getAggregateId(), row.getAttemptCount());
  }

  public boolean heartbeat(Work work) {
    OutboxEvent row = s.get(OutboxEvent.class, work.eventId());
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    if (!"PROCESSING".equals(row.getStatus()) || row.getAttemptCount() != work.attempt())
      return false;
    row.setNextAttemptAt(Instant.now().plusSeconds(600));
    return true;
  }

  public void finish(Work work, boolean success) {
    OutboxEvent row = s.get(OutboxEvent.class, work.eventId());
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    if (row.getAttemptCount() != work.attempt()) return;
    if (success) {
      row.setStatus("SENT");
      row.setSentAt(Instant.now());
    } else {
      row.setStatus(row.getAttemptCount() >= 3 ? "FAILED" : "PENDING");
      row.setNextAttemptAt(Instant.now().plusSeconds(30L * row.getAttemptCount()));
    }
  }
}
