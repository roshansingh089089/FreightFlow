package com.freightflow;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;import org.springframework.http.converter.HttpMessageNotReadableException;import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.*;import java.util.*;
class ApiException extends RuntimeException {final int status;final String code;ApiException(int s,String c,String m){super(m);status=s;code=c;}}
@RestControllerAdvice class ApiErrors {
 @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e){return ResponseEntity.status(e.status).body(Map.of("timestamp",Instant.now(),"status",e.status,"code",e.code,"message",e.getMessage(),"fieldErrors",Map.of()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){Map<String,String> fields=new LinkedHashMap<>();e.getBindingResult().getFieldErrors().forEach(f->fields.putIfAbsent(f.getField(),f.getDefaultMessage()));return ResponseEntity.badRequest().body(Map.of("timestamp",Instant.now(),"status",400,"code","VALIDATION_ERROR","message","Please correct the highlighted fields","fieldErrors",fields));}
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<?> malformed(){return ResponseEntity.badRequest().body(Map.of("timestamp",Instant.now(),"status",400,"code","VALIDATION_ERROR","message","Invalid request body or parameter","fieldErrors",Map.of()));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("timestamp",Instant.now(),"status",409,"code","CONFLICT","message","This record conflicts with existing data","fieldErrors",Map.of()));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> other(Exception e){return ResponseEntity.status(500).body(Map.of("timestamp",Instant.now(),"status",500,"code","SERVER_ERROR","message","An unexpected error occurred","fieldErrors",Map.of()));}
}
