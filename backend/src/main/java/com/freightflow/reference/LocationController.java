package com.freightflow.reference;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reference/locations")
class LocationController {
  private final LocationSearchService locations;

  LocationController(LocationSearchService locations) {
    this.locations = locations;
  }

  @GetMapping
  List<LocationSearchService.LocationView> search(
      @RequestParam String q, @RequestParam(defaultValue = "ALL") String mode) {
    return locations.search(q, mode);
  }
}
