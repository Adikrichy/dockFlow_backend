package org.aldousdev.dockflowbackend.auth.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends RuntimeException {
     @ExceptionHandler(EmailAlreadyExistsException.class)
     public ResponseEntity <?> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex){
         return ResponseEntity.status(HttpStatus.CONFLICT)
                 .body(ex.getMessage());
     }

     @ExceptionHandler(RuntimeException.class)
     public ResponseEntity <?> handleRuntimeException(RuntimeException ex){
         return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                 .body(ex.getMessage());
     }
}
