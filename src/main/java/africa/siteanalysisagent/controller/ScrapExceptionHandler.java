package africa.siteanalysisagent.controller;

import africa.siteanalysisagent.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDate;
import java.util.Map;

@ControllerAdvice
public class ScrapExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Object> exceptionHandler(IllegalArgumentException illegalArgumentException) {
        ApiResponse<Map<String, String>> response = new ApiResponse<>();
        response.setMessage(illegalArgumentException.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setTimestamp(LocalDate.now());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
