package com.freightflow.reference;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LocationImportService implements ApplicationRunner {
  private static final String VERSION = "2025-1";
  private final JdbcTemplate jdbc;
  private final boolean importOnStartup;

  public LocationImportService(
      JdbcTemplate jdbc,
      @Value("${reference.unlocode.import-on-startup:true}") boolean importOnStartup) {
    this.jdbc = jdbc;
    this.importOnStartup = importOnStartup;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    if (!importOnStartup) return;
    Integer count =
        jdbc.queryForObject(
            "select count(*) from reference_locations where source_version = ?",
            Integer.class,
            VERSION);
    if (count != null && count >= 116067) return;
    importRelease();
  }

  public void importRelease() throws Exception {
    var resource = new ClassPathResource("reference/unlocode-2025-1.tsv.gz");
    try (var reader =
        new BufferedReader(
            new InputStreamReader(
                new GZIPInputStream(resource.getInputStream()), StandardCharsets.UTF_8))) {
      List<String[]> batch = new ArrayList<>(500);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 5) throw new IllegalStateException("Invalid UN/LOCODE resource row");
        batch.add(parts);
        if (batch.size() == 500) {
          save(batch);
          batch.clear();
        }
      }
      if (!batch.isEmpty()) save(batch);
    }
  }

  private void save(List<String[]> batch) {
    jdbc.batchUpdate(
        "insert into reference_locations(code,country_code,name,aliases,functions,source_version) "
            + "values (?,?,?,?,?,?) on conflict(code) do update set "
            + "country_code=excluded.country_code,name=excluded.name,aliases=excluded.aliases,"
            + "functions=excluded.functions,source_version=excluded.source_version,updated_at=now()",
        batch,
        batch.size(),
        (statement, row) -> {
          for (int i = 0; i < 5; i++) statement.setString(i + 1, row[i]);
          statement.setString(6, VERSION);
        });
  }
}
