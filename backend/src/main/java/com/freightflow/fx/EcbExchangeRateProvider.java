package com.freightflow.fx;

import com.freightflow.common.exception.ApiException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EcbExchangeRateProvider {
  private static final Logger logger = LoggerFactory.getLogger(EcbExchangeRateProvider.class);
  private static final URI ENDPOINT =
      URI.create(
          "https://data-api.ecb.europa.eu/service/data/EXR/D..EUR.SP00.A?lastNObservations=1&format=csvdata");
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private volatile HttpClient currentEcbChainClient;

  public record Observation(LocalDate date, BigDecimal unitsPerEur) {}

  public Map<String, Observation> latest() {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(ENDPOINT)
              .timeout(Duration.ofSeconds(12))
              .header("Accept", "text/csv")
              .build();
      HttpResponse<String> response;
      try {
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
      } catch (SSLHandshakeException defaultTrustFailed) {
        // Older JDK CA stores can lack the current public root used by ECB. Keep hostname
        // verification and use this root only for a retry of the fixed ECB endpoint.
        logger.warn(
            "Default JVM trust store could not validate ECB TLS; retrying with current public root");
        response = currentEcbChainClient().send(request, HttpResponse.BodyHandlers.ofString());
      }
      if (response.statusCode() != 200)
        throw new ApiException(503, "ECB_UNAVAILABLE", "ECB reference rates are unavailable");
      return parse(response.body());
    } catch (ApiException exception) {
      throw exception;
    } catch (Exception exception) {
      logger.warn("ECB reference-rate request failed: {}", exception.toString());
      throw new ApiException(503, "ECB_UNAVAILABLE", "ECB reference rates are unavailable");
    }
  }

  private synchronized HttpClient currentEcbChainClient() throws Exception {
    if (currentEcbChainClient != null) return currentEcbChainClient;
    KeyStore roots = KeyStore.getInstance(KeyStore.getDefaultType());
    roots.load(null, null);
    try (InputStream pem =
        EcbExchangeRateProvider.class.getResourceAsStream(
            "/fx/sectigo-public-server-authentication-root-e46.pem")) {
      if (pem == null) throw new IllegalStateException("ECB public CA certificate is missing");
      roots.setCertificateEntry(
          "sectigo-public-server-authentication-root-e46",
          CertificateFactory.getInstance("X.509").generateCertificate(pem));
    }
    TrustManagerFactory managers =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    managers.init(roots);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, managers.getTrustManagers(), null);
    currentEcbChainClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).sslContext(context).build();
    return currentEcbChainClient;
  }

  static Map<String, Observation> parse(String csv) {
    Map<String, Observation> rates = new HashMap<>();
    for (String row : csv.split("\\R")) {
      String[] columns = row.split(",", 9);
      if (columns.length < 8 || !columns[0].startsWith("EXR.D.")) continue;
      String currency = columns[2];
      if (!currency.matches("[A-Z]{3}") || !"EUR".equals(columns[3])) continue;
      BigDecimal value = new BigDecimal(columns[7]);
      if (value.signum() <= 0) continue;
      rates.put(currency, new Observation(LocalDate.parse(columns[6]), value));
    }
    if (rates.isEmpty())
      throw new ApiException(503, "ECB_INVALID_RESPONSE", "ECB returned no reference rates");
    return rates;
  }
}
