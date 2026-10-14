package com.freightflow.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class QuotationMailDraftServiceTest {
  @Test
  void concurrentLegacyRequestsWithSameKeyReturnOneSubmission() throws Exception {
    assertNotNull(
        QuotationMailDraftService.class
            .getDeclaredMethod(
                "submitLegacy",
                UUID.class,
                UUID.class,
                String.class,
                java.util.function.Supplier.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class)
            .getAnnotation(Transactional.class));
    OutboundMailService drafts = mock(OutboundMailService.class);
    MailAttachmentService attachments = mock(MailAttachmentService.class);
    QuotationMailDraftService service = spy(new QuotationMailDraftService(drafts, attachments));
    UUID quote = UUID.randomUUID(), customer = UUID.randomUUID(), id = UUID.randomUUID();
    String key = "same-key";
    java.util.concurrent.locks.ReentrantLock rowLock =
        new java.util.concurrent.locks.ReentrantLock();
    AtomicReference<DraftResponse> saved = new AtomicReference<>();
    CountDownLatch entered = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              rowLock.lock();
              entered.countDown();
              return null;
            })
        .when(drafts)
        .lockQuotationForLegacy(quote);
    when(drafts.existingSubmission(key, quote))
        .thenAnswer(
            invocation -> {
              DraftResponse existing = saved.get();
              if (existing != null) rowLock.unlock();
              return existing;
            });
    DraftResponse queued = mock(DraftResponse.class);
    when(queued.id()).thenReturn(id);
    when(queued.status()).thenReturn("QUEUED");
    doReturn(id)
        .when(service)
        .create(
            eq(quote),
            eq(customer),
            anyString(),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            isNull());
    when(drafts.queue(id, key))
        .thenAnswer(
            invocation -> {
              saved.set(queued);
              rowLock.unlock();
              return queued;
            });
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first =
          executor.submit(
              () ->
                  service.submitLegacy(
                      quote,
                      customer,
                      "quote.pdf",
                      () -> new byte[] {1},
                      "buyer@example.test",
                      "",
                      "Subject",
                      "Body",
                      key));
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      var second =
          executor.submit(
              () ->
                  service.submitLegacy(
                      quote,
                      customer,
                      "quote.pdf",
                      () -> new byte[] {1},
                      "buyer@example.test",
                      "",
                      "Subject",
                      "Body",
                      key));
      assertEquals(Map.of("status", "QUEUED", "draftId", id), first.get(2, TimeUnit.SECONDS));
      assertEquals(Map.of("status", "QUEUED", "draftId", id), second.get(2, TimeUnit.SECONDS));
      verify(service, times(1))
          .create(
              any(),
              any(),
              anyString(),
              any(),
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              isNull());
      verify(drafts, times(1)).queue(id, key);
    } finally {
      executor.shutdownNow();
    }
  }
}
