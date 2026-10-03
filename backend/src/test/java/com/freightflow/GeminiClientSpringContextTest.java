package com.freightflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
class GeminiClientSpringContextTest {
 @Test void springSelectsConfiguredConstructorWithKeyAbsent(){
  new ApplicationContextRunner().withBean(ObjectMapper.class,ObjectMapper::new).withBean(GeminiClient.class)
   .withPropertyValues("gemini.api-key=","gemini.model=gemini-3.5-flash-lite")
   .run(context->{assertThat(context).hasNotFailed();assertThat(context).hasSingleBean(GeminiClient.class);assertThat(context.getBean(GeminiClient.class).configured()).isFalse();});
 }
}
