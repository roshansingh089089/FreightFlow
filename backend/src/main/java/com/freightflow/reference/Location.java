package com.freightflow.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reference_locations")
@Getter
@Setter
public class Location {
  @Id String code;
  String countryCode;
  String name;

  @Column(columnDefinition = "text")
  String aliases;

  String functions;
  String sourceVersion;
  Instant updatedAt;
}
