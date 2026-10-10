package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.entity.MediaAsset;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

/** 有界后台Worker；租约定期续期，外部调用不持有业务数据库锁。 */
@Component
@EnableScheduling
public class MiniTaskWorker {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(MiniTaskWorker.class);
  private final MiniOutboxService outbox;
  private final MiniProcessingService processing;
  private final MiniPrintService prints;
  private final MiniOcrGateway ocr;
  private final MiniPdfRenderer renderer;
  private final MiniAssetService assets;
  private final MiniSupport s;
  private final MiniAdminService admin;
  private final boolean enabled;
  private final AtomicInteger active = new AtomicInteger();
  private final ExecutorService executor =
      Executors.newFixedThreadPool(
          2,
          r -> {
            Thread t = new Thread(r, "mini-task-worker");
            t.setDaemon(true);
            return t;
          });
  private final ScheduledExecutorService leases =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "mini-task-lease");
            t.setDaemon(true);
            return t;
          });

  public MiniTaskWorker(
      MiniOutboxService outbox,
      MiniProcessingService processing,
      MiniPrintService prints,
      MiniOcrGateway ocr,
      MiniPdfRenderer renderer,
      MiniAssetService assets,
      MiniSupport s,
      MiniAdminService admin,
      @Value("${app.mini.worker-enabled:true}") boolean enabled) {
    this.outbox = outbox;
    this.processing = processing;
    this.prints = prints;
    this.ocr = ocr;
    this.renderer = renderer;
    this.assets = assets;
    this.s = s;
    this.admin = admin;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelayString = "${app.mini.poll-delay-ms:1000}")
  public void poll() {
    if (!enabled || active.get() >= 2) return;
    try {
      MiniOutboxService.Work work = outbox.claim();
      if (work == null) return;
      active.incrementAndGet();
      executor.submit(() -> run(work));
    } catch (Exception e) {
      log.warn("[小程序任务] 认领消息失败", e);
    }
  }

  private void run(MiniOutboxService.Work work) {
    ScheduledFuture<?> lease =
        leases.scheduleAtFixedRate(
            () -> {
              try {
                outbox.heartbeat(work);
              } catch (Exception e) {
                log.warn("[小程序任务] 租约续期失败 eventId={}", work.eventId());
              }
            },
            60,
            60,
            TimeUnit.SECONDS);
    boolean success = false;
    try {
      if (work.type().equals("PROCESSING_JOB")) {
        for (String itemId : processing.itemIds(work.id())) {
          if (!outbox.heartbeat(work)) return;
          MiniProcessingService.ItemInput input = processing.begin(itemId);
          if (input == null) continue;
          try {
            MediaAsset asset = s.get(MediaAsset.class, input.assetId());
            processing.succeeded(input, ocr.process(input, assets.bytes(asset)));
          } catch (Exception e) {
            processing.failed(input, e);
          }
        }
      } else if (work.type().equals("PRINT_TASK")) {
        MiniPrintService.RenderInput input = prints.beginRender(work.id());
        if (input != null) {
          try {
            MiniPdfRenderer.Output output = renderer.render(input);
            if (outbox.heartbeat(work))
              prints.rendered(input.taskId(), output.bytes(), output.pageCount());
          } catch (Exception e) {
            prints.renderFailed(input.taskId(), e.getMessage());
          }
        }
      } else if (work.type().equals("ASSET")) {
        admin.deleteAsset(work.id());
      } else throw new IllegalArgumentException("未知消息类型");
      success = true;
    } catch (Exception e) {
      log.error("[小程序任务] 执行失败 eventId={}", work.eventId(), e);
    } finally {
      lease.cancel(false);
      try {
        outbox.finish(work, success);
      } finally {
        active.decrementAndGet();
      }
    }
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
    leases.shutdownNow();
  }
}
