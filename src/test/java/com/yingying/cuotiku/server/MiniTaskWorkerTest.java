package com.yingying.cuotiku.server;

import static org.mockito.Mockito.*;

import com.yingying.cuotiku.server.mini.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证异步执行与租约失效边界，避免取消后的旧Worker发布文件。 */
class MiniTaskWorkerTest {
  @Test
  void rendersAndAcknowledgesPersistedPrintMessage() throws Exception {
    MiniOutboxService outbox = mock(MiniOutboxService.class);
    MiniPrintService prints = mock(MiniPrintService.class);
    MiniPdfRenderer renderer = mock(MiniPdfRenderer.class);
    var work = new MiniOutboxService.Work("event", "PRINT_TASK", "task", 1);
    var input = new MiniPrintService.RenderInput("task", 1L, "student", null, List.of(), 1);
    byte[] pdf = new byte[] {1, 2, 3};
    when(outbox.claim()).thenReturn(work, (MiniOutboxService.Work) null);
    when(outbox.heartbeat(work)).thenReturn(true);
    when(prints.beginRender("task")).thenReturn(input);
    when(renderer.render(input)).thenReturn(new MiniPdfRenderer.Output(pdf, 1));
    try (WorkerScope scope = new WorkerScope(outbox, prints, renderer)) {
      scope.worker.poll();
      verify(prints, timeout(3000)).rendered("task", pdf, 1);
      verify(outbox, timeout(3000)).finish(work, true);
    }
  }

  @Test
  void expiredLeaseCannotPublishAndRenderingFailureIsVisible() throws Exception {
    MiniOutboxService outbox = mock(MiniOutboxService.class);
    MiniPrintService prints = mock(MiniPrintService.class);
    MiniPdfRenderer renderer = mock(MiniPdfRenderer.class);
    var input = new MiniPrintService.RenderInput("task", 1L, "student", null, List.of(), 1);
    var expired = new MiniOutboxService.Work("expired", "PRINT_TASK", "task", 1);
    when(outbox.claim()).thenReturn(expired);
    when(outbox.heartbeat(expired)).thenReturn(false);
    when(prints.beginRender("task")).thenReturn(input);
    when(renderer.render(input)).thenReturn(new MiniPdfRenderer.Output(new byte[] {1}, 1));
    try (WorkerScope scope = new WorkerScope(outbox, prints, renderer)) {
      scope.worker.poll();
      verify(outbox, timeout(3000)).finish(expired, true);
      verify(prints, never()).rendered(anyString(), any(), anyInt());
    }
    reset(outbox, prints, renderer);
    var failure = new MiniOutboxService.Work("failed", "PRINT_TASK", "task", 2);
    when(outbox.claim()).thenReturn(failure);
    when(prints.beginRender("task")).thenReturn(input);
    when(renderer.render(input)).thenThrow(new java.io.IOException("invalid image"));
    try (WorkerScope scope = new WorkerScope(outbox, prints, renderer)) {
      scope.worker.poll();
      verify(prints, timeout(3000)).renderFailed("task", "invalid image");
      verify(outbox, timeout(3000)).finish(failure, true);
    }
  }

  private static class WorkerScope implements AutoCloseable {
    final MiniTaskWorker worker;

    WorkerScope(MiniOutboxService outbox, MiniPrintService prints, MiniPdfRenderer renderer) {
      worker =
          new MiniTaskWorker(
              outbox,
              mock(MiniProcessingService.class),
              prints,
              mock(MiniOcrGateway.class),
              renderer,
              mock(MiniAssetService.class),
              mock(MiniSupport.class),
              mock(MiniAdminService.class),
              true);
    }

    public void close() {
      worker.close();
    }
  }
}
