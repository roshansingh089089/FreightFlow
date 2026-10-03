package com.freightflow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/ai") class AiController {
 private final AiShipmentService shipments;private final AiAssistanceService assistance;
 AiController(AiShipmentService shipments,AiAssistanceService assistance){this.shipments=shipments;this.assistance=assistance;}
 record ShipmentText(@NotBlank String text){}
 record EnquiryId(@jakarta.validation.constraints.NotNull UUID enquiryId){}
 record SearchText(@NotBlank String text){}
 @PostMapping("/shipment-extract") Object extract(@Valid @RequestBody ShipmentText request){SecurityConfig.current();if(request.text().length()>10000)throw new ApiException(413,"AI_INPUT_TOO_LARGE","Shipment text must be 10,000 characters or less.");return assistance.tracked("SHIPMENT_EXTRACTION",null,null,()->{AiShipmentExtraction extracted=shipments.extract(request.text());return Map.of("extracted",extracted,"missingFields",extracted.missingFields(),"warnings",extracted.warnings());});}
 @PostMapping("/draft-clarification") Object clarification(@Valid @RequestBody EnquiryId request){return assistance.draftClarification(request.enquiryId());}
 @PostMapping("/quotations/{id}/text") Object quoteText(@PathVariable UUID id){return assistance.quoteText(id,"text");}
 @PostMapping("/quotations/{id}/check") Object quoteCheck(@PathVariable UUID id){return assistance.quoteCheck(id);}
 @PostMapping("/quotations/{id}/email") Object quoteEmail(@PathVariable UUID id){return assistance.quoteText(id,"email");}
 @PostMapping("/quotations/{id}/terms") Object quoteTerms(@PathVariable UUID id){return assistance.quoteText(id,"terms");}
 @PostMapping("/customers/{id}/summary") Object customerSummary(@PathVariable UUID id){return assistance.customerSummary(id);}
 @PostMapping("/search") Object search(@Valid @RequestBody SearchText request){if(request.text().length()>500)throw new ApiException(413,"AI_INPUT_TOO_LARGE","Search text must be 500 characters or less.");return assistance.naturalSearch(request.text());}
}
