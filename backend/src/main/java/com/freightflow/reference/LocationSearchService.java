package com.freightflow.reference;

import com.freightflow.common.exception.ApiException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class LocationSearchService {
  private final LocationRepository locations;

  public LocationSearchService(LocationRepository locations) {
    this.locations = locations;
  }

  public record LocationView(
      String code,
      String name,
      String countryCode,
      String countryName,
      List<String> locationTypes,
      String functions,
      String sourceVersion) {
    static LocationView of(Location location) {
      String countryCode = location.getCountryCode();
      String countryName = Locale.of("", countryCode).getDisplayCountry(Locale.ENGLISH);
      String functions = location.getFunctions() == null ? "" : location.getFunctions();
      List<String> locationTypes = new java.util.ArrayList<>();
      if (functions.startsWith("1")) locationTypes.add("PORT");
      if (functions.length() >= 4 && functions.charAt(3) == '4') locationTypes.add("AIRPORT");
      if (locationTypes.isEmpty()) locationTypes.add("PLACE");
      return new LocationView(
          location.getCode(),
          location.getName(),
          countryCode,
          countryName.isBlank() ? countryCode : countryName,
          locationTypes,
          functions,
          location.getSourceVersion());
    }
  }

  public List<LocationView> search(String text, String mode) {
    String term = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    if (term.contains(",")) term = term.substring(0, term.indexOf(',')).strip();
    for (String country : Locale.getISOCountries()) {
      if (Locale.of("", country).getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(term)) {
        term = country.toLowerCase(Locale.ROOT);
        break;
      }
    }
    if (term.length() < 2 || term.length() > 100) return List.of();
    String kind = normalizeMode(mode);
    return locations.search(term, kind).stream().map(LocationView::of).toList();
  }

  public Location require(String code, String mode) {
    if (code == null || !code.matches("[A-Za-z]{2}[A-Za-z0-9]{3}"))
      throw new ApiException(400, "LOCATION_REQUIRED", "Select a canonical location");
    Location location =
        locations
            .findById(code.toUpperCase(Locale.ROOT))
            .orElseThrow(
                () -> new ApiException(400, "LOCATION_UNKNOWN", "Location code is not recognized"));
    String kind = normalizeMode(mode);
    if (("SEA".equals(kind) && !location.getFunctions().startsWith("1"))
        || ("AIR".equals(kind)
            && (location.getFunctions().length() < 4 || location.getFunctions().charAt(3) != '4')))
      throw new ApiException(400, "LOCATION_MODE", "Location does not support this mode");
    return location;
  }

  private String normalizeMode(String mode) {
    if ("OCEAN".equalsIgnoreCase(mode) || "SEA".equalsIgnoreCase(mode)) return "SEA";
    if ("AIR".equalsIgnoreCase(mode)) return "AIR";
    return "ALL";
  }
}
