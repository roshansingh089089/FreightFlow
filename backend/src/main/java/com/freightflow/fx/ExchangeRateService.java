package com.freightflow.fx;

import com.freightflow.common.exception.ApiException;
import com.freightflow.common.security.SecurityConfig;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRateService {
  private final JdbcTemplate jdbc;
  private final EcbExchangeRateProvider provider;

  public ExchangeRateService(JdbcTemplate jdbc, EcbExchangeRateProvider provider) {
    this.jdbc = jdbc;
    this.provider = provider;
  }

  @Scheduled(cron = "0 15 17 * * MON-FRI", zone = "Europe/Berlin")
  public void refreshAfterEcbPublication() {
    refreshIfDue();
  }

  public record Rate(BigDecimal value, String source, LocalDate date) {}

  public Rate rate(String from, String to) {
    String source = validCurrency(from);
    String target = validCurrency(to);
    if (source.equals(target)) return new Rate(BigDecimal.ONE, "SAME_CURRENCY", LocalDate.now());
    ApiException providerFailure = refreshIfDue();
    Rate first = latest(source);
    Rate second = latest(target);
    if (first != null
        && second != null
        && (source.equals("EUR") || target.equals("EUR") || first.date().equals(second.date()))) {
      LocalDate date = source.equals("EUR") ? second.date() : first.date();
      if (isFresh(date, LocalDate.now()))
        return new Rate(crossRate(first.value(), second.value()), "ECB_REFERENCE", date);
    }
    UUID tenant = SecurityConfig.current().getTenantId();
    var manual =
        jdbc.query(
            "select rate,effective_date from fx_manual_rates where tenant_id=? and from_currency=? "
                + "and to_currency=? and effective_date<=current_date order by effective_date desc,created_at desc limit 1",
            (rs, row) -> new Rate(rs.getBigDecimal(1), "MANUAL", rs.getDate(2).toLocalDate()),
            tenant,
            source,
            target);
    if (!manual.isEmpty()) return manual.getFirst();
    if (providerFailure != null) throw providerFailure;
    throw new ApiException(
        422,
        "FX_RATE_UNAVAILABLE",
        "No current ECB or authorized manual rate for this currency pair");
  }

  private ApiException refreshIfDue() {
    Instant fetched =
        jdbc.query(
            "select max(fetched_at) from fx_reference_rates",
            rs -> {
              if (!rs.next()) return null;
              var timestamp = rs.getTimestamp(1);
              return timestamp == null ? null : timestamp.toInstant();
            });
    if (fetched != null && fetched.isAfter(Instant.now().minus(12, ChronoUnit.HOURS))) return null;
    try {
      Map<String, EcbExchangeRateProvider.Observation> rates = provider.latest();
      for (var entry : rates.entrySet()) {
        jdbc.update(
            "insert into fx_reference_rates(currency,observation_date,units_per_eur,source,fetched_at) "
                + "values (?,?,?,'ECB_REFERENCE',now()) on conflict(currency,observation_date) do update "
                + "set units_per_eur=excluded.units_per_eur,fetched_at=now()",
            entry.getKey(),
            Date.valueOf(entry.getValue().date()),
            entry.getValue().unitsPerEur());
      }
      return null;
    } catch (ApiException failure) {
      // A previously stored observation may remain valid over weekends and publication holidays.
      return failure;
    }
  }

  private Rate latest(String currency) {
    if ("EUR".equals(currency)) return new Rate(BigDecimal.ONE, "ECB_REFERENCE", LocalDate.now());
    var rows =
        jdbc.query(
            "select units_per_eur,observation_date from fx_reference_rates where currency=? "
                + "order by observation_date desc limit 1",
            (rs, row) ->
                new Rate(rs.getBigDecimal(1), "ECB_REFERENCE", rs.getDate(2).toLocalDate()),
            currency);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public static String validCurrency(String currency) {
    String normalized = currency == null ? "" : currency.toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z]{3}"))
      throw new ApiException(400, "INVALID_CURRENCY", "Use a three-letter currency code");
    return normalized;
  }

  static BigDecimal crossRate(BigDecimal fromUnitsPerEur, BigDecimal toUnitsPerEur) {
    return toUnitsPerEur.divide(fromUnitsPerEur, 10, java.math.RoundingMode.HALF_UP);
  }

  static boolean isFresh(LocalDate observation, LocalDate today) {
    long age = ChronoUnit.DAYS.between(observation, today);
    return age >= 0 && age <= 7;
  }
}
