package com.freightflow.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.freightflow.common.exception.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LocationSearchServiceTest {
  @Test
  void returnsAllAmbiguousMatchesForEmployeeSelection() {
    var repository = Mockito.mock(LocationRepository.class);
    var first = location("INNSA", "Nhava Sheva (Jawaharlal Nehru)", "1-------");
    var second = location("INBOM", "Mumbai (ex Bombay)", "123456--");
    when(repository.search("mumbai", "SEA")).thenReturn(List.of(first, second));
    var results = new LocationSearchService(repository).search("Mumbai", "OCEAN");
    assertEquals(2, results.size());
  }

  @Test
  void rejectsNonPortForOcean() {
    var repository = Mockito.mock(LocationRepository.class);
    when(repository.findById("FRCDG"))
        .thenReturn(Optional.of(location("FRCDG", "Paris Charles de Gaulle", "---4----")));
    var service = new LocationSearchService(repository);
    assertThrows(ApiException.class, () -> service.require("FRCDG", "OCEAN"));
  }

  private static Location location(String code, String name, String functions) {
    var location = new Location();
    location.setCode(code);
    location.setName(name);
    location.setCountryCode(code.substring(0, 2));
    location.setFunctions(functions);
    location.setSourceVersion("2025-1");
    return location;
  }
}
