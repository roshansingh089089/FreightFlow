package com.freightflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit.jupiter.EnabledIf;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf(expression = "#{systemEnvironment['DATABASE_URL'] != null}", loadContext = false)
class NullableFilterRepositoryTest {
 @Autowired EnquiryRepo enquiries;
 @Autowired QuoteRepo quotations;

 @Test void enquiryListAcceptsNullCustomerAndDateFilters() {
  assertDoesNotThrow(() -> enquiries.search(UUID.randomUUID(), "", null, "", "", null, PageRequest.of(0, 10)));
 }

 @Test void enquiryListAcceptsSuppliedFilters() {
  assertDoesNotThrow(() -> enquiries.search(UUID.randomUUID(), "Mumbai", UUID.randomUUID(), "OCEAN", "NEW", Instant.now(), PageRequest.of(0, 10)));
 }

 @Test void quotationListAcceptsSuppliedFilters() {
  assertDoesNotThrow(() -> quotations.search(UUID.randomUUID(), "QT-", UUID.randomUUID(), "DRAFT", Instant.now(), PageRequest.of(0, 10)));
 }

 @Test void quotationListAcceptsNullCustomerAndDateFilters() {
  assertDoesNotThrow(() -> quotations.search(UUID.randomUUID(), "", null, "", null, PageRequest.of(0, 10)));
 }
}
