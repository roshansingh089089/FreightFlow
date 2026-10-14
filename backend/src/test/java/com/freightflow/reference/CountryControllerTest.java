package com.freightflow.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CountryControllerTest {
  @Test
  void searchesCountriesByNameAndCode() {
    var controller = new CountryController();
    assertTrue(
        controller.list("ind").stream()
            .anyMatch(country -> country.code().equals("IN") && country.name().equals("India")));
    assertEquals(
        "India",
        controller.list("IN").stream()
            .filter(country -> country.code().equals("IN"))
            .findFirst()
            .orElseThrow()
            .name());
  }
}
