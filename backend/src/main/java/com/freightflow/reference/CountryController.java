package com.freightflow.reference;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reference/countries")
class CountryController {
  record CountryView(String code, String name) {}

  @GetMapping
  List<CountryView> list(@RequestParam(defaultValue = "") String q) {
    String term = q.strip().toLowerCase(Locale.ROOT);
    if (term.length() > 100) return List.of();
    return Arrays.stream(Locale.getISOCountries())
        .map(code -> new CountryView(code, Locale.of("", code).getDisplayCountry(Locale.ENGLISH)))
        .filter(
            country ->
                country.code().toLowerCase(Locale.ROOT).contains(term)
                    || country.name().toLowerCase(Locale.ROOT).contains(term))
        .sorted(Comparator.comparing(CountryView::name))
        .toList();
  }
}
