package com.freightflow;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.List;

@Configuration
@Profile("prod")
class ProdDatabaseConfiguration {
 @Bean
 static BeanFactoryPostProcessor requireProductionDatabase(Environment environment) {
  return factory -> {
   List<String> missing=List.of("DATABASE_URL","DATABASE_USER","DATABASE_PASSWORD").stream()
    .filter(name -> environment.getProperty(name)==null || environment.getProperty(name).isBlank()).toList();
   if(!missing.isEmpty())
    throw new IllegalStateException("Production database configuration is missing: "+String.join(", ",missing));
   if(!environment.getRequiredProperty("DATABASE_URL").startsWith("jdbc:postgresql://"))
    throw new IllegalStateException("Production DATABASE_URL must be a PostgreSQL JDBC URL");
  };
 }
}
