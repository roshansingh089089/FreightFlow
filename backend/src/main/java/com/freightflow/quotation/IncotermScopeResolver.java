package com.freightflow.quotation;

import com.freightflow.common.exception.ApiException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Default quotation services; employees may customize the saved scope. */
public final class IncotermScopeResolver {
  public static final String PICKUP = "Pickup & trucking";
  public static final String EXPORT = "Export customs clearance";
  public static final String ORIGIN = "Origin charges";
  public static final String DESTINATION = "Destination charges";
  public static final String HAULAGE = "Haulage to final place";
  public static final String IMPORT = "Import customs clearance";
  public static final String DELIVERY = "Door delivery";
  public static final String DUTIES = "Duties & taxes";
  public static final String INSURANCE = "Cargo insurance";

  private static final Map<String, List<String>> PRESETS =
      Map.of(
          "EXW", List.of(PICKUP, EXPORT, ORIGIN, "freight"),
          "FCA", List.of("freight", DESTINATION, HAULAGE),
          "FOB", List.of("freight", DESTINATION, HAULAGE),
          "CFR", List.of(ORIGIN, "freight"),
          "CPT", List.of(ORIGIN, "freight"),
          "CIF", List.of(ORIGIN, "freight", INSURANCE),
          "CIP", List.of(ORIGIN, "freight", INSURANCE),
          "DAP", List.of(EXPORT, ORIGIN, "freight", DESTINATION, HAULAGE, DELIVERY),
          "DPU", List.of(EXPORT, ORIGIN, "freight", DESTINATION, HAULAGE, DELIVERY),
          "DDP",
              List.of(
                  PICKUP,
                  EXPORT,
                  ORIGIN,
                  "freight",
                  DESTINATION,
                  HAULAGE,
                  IMPORT,
                  DELIVERY,
                  DUTIES,
                  INSURANCE));

  private IncotermScopeResolver() {}

  public static boolean supported(String term) {
    return term != null && PRESETS.containsKey(term.toUpperCase(java.util.Locale.ROOT));
  }

  public static String freight(String mode) {
    return "AIR".equals(mode) ? "Air freight" : isRoad(mode) ? "Road freight" : "Ocean Freight";
  }

  public static boolean isRoad(String mode) {
    return "ROAD".equals(mode) || "FTL".equals(mode) || "LTL".equals(mode);
  }

  public static List<String> defaults(String term, String mode, boolean hasFpod) {
    if (!supported(term))
      throw new ApiException(400, "INVALID_INCOTERM", "Select a supported Incoterm");
    Set<String> result = new LinkedHashSet<>();
    for (String item : PRESETS.get(term.toUpperCase(java.util.Locale.ROOT))) {
      if (HAULAGE.equals(item) && (!hasFpod || isRoad(mode))) continue;
      if (isRoad(mode) && Set.of(ORIGIN, DESTINATION, HAULAGE).contains(item)) continue;
      result.add("freight".equals(item) ? freight(mode) : item);
    }
    return List.copyOf(result);
  }

  public static void validateScope(List<String> scope, String mode, boolean hasFpod) {
    if (scope == null) return;
    Set<String> allowed =
        new LinkedHashSet<>(
            List.of(
                PICKUP,
                EXPORT,
                ORIGIN,
                freight(mode),
                DESTINATION,
                IMPORT,
                DELIVERY,
                DUTIES,
                INSURANCE,
                "Fumigation",
                "Certificate of Origin",
                "Warehousing"));
    if (hasFpod && !isRoad(mode)) allowed.add(HAULAGE);
    if (isRoad(mode)) allowed.removeAll(Set.of(ORIGIN, DESTINATION));
    if (scope.stream().anyMatch(item -> !allowed.contains(item)))
      throw new ApiException(
          400, "INVALID_SCOPE", "Scope contains a service that does not apply to this shipment");
  }

  public static boolean referenceDestinationAllowed(String term) {
    return Set.of("CIF", "CFR", "CPT", "CIP").contains(term);
  }
}
