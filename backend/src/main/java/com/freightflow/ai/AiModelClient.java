package com.freightflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface AiModelClient {
  JsonNode generate(String instruction, String input, Map<String, Object> schema);
}
