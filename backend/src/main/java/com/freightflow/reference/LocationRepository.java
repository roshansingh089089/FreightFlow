package com.freightflow.reference;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, String> {
  @Query(
      value =
          "select * from reference_locations where "
              + "(:mode = 'ALL' or (:mode = 'SEA' and substring(functions,1,1) = '1') "
              + "or (:mode = 'AIR' and substring(functions,4,1) = '4')) "
              + "and (lower(code) = :term or lower(country_code) = :term "
              + "or lower(name) like '%' || :term || '%' "
              + "or lower(aliases) like '%' || :term || '%') "
              + "order by case when lower(code) = :term then 0 "
              + "when lower(name) = :term then 1 "
              + "when lower(name) like :term || '%' then 2 else 3 end, name, code limit 12",
      nativeQuery = true)
  List<Location> search(@Param("term") String term, @Param("mode") String mode);
}
