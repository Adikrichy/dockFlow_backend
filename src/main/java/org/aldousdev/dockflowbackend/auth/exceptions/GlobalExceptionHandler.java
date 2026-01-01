package org.aldousdev.dockflowbackend.auth.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.workflow.exceptions.InvalidFileException;
import org.aldousdev.dockflowbackend.workflow.exceptions.DocumentUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

     @ExceptionHandler(EmailAlreadyExistsException.class)
     public ResponseEntity<?> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex){
         log.warn("Email already exists: {}", ex.getMessage());
         return ResponseEntity.status(HttpStatus.CONFLICT)
                 .body("Error: " + ex.getMessage());
     }

     @ExceptionHandler(UserNotActiveException.class)
     public ResponseEntity<?> handleUserNotActiveException(UserNotActiveException ex){
         log.warn("User not active: {}", ex.getMessage());
         return ResponseEntity.status(HttpStatus.FORBIDDEN)
                 .body("Error: " + ex.getMessage());
     }

     @ExceptionHandler(CompanyAccessDeniedException.class)
     public ResponseEntity<?> handleCompanyAccessDeniedException(CompanyAccessDeniedException ex){
         log.warn("Company access denied: {}", ex.getMessage());
         return ResponseEntity.status(HttpStatus.FORBIDDEN)
                 .body("Error: " + ex.getMessage());
     }

     @ExceptionHandler(CompanyNotFoundException.class)
     public ResponseEntity<?> handleCompanyNotFoundException(CompanyNotFoundException ex){
         log.warn("Company not found: {}", ex.getMessage());
         return ResponseEntity.status(HttpStatus.NOT_FOUND)
                 .body("Error: " + ex.getMessage());
     }

     @ExceptionHandler(InvalidFileException.class)
     public ResponseEntity<?> handleInvalidFileException(InvalidFileException ex){
         log.warn("Invalid file: {}", ex.getMessage());
         return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                 .body("Error: " + ex.getMessage());
     }

     @ExceptionHandler(DocumentUploadException.class)
     public ResponseEntity<?> handleDocumentUploadException(DocumentUploadException ex){
         log.error("Document upload error: {}", ex.getMessage(), ex);
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                 .body("Error: Failed to upload document");
     }

     @ExceptionHandler(ResourceNotFoundException.class)
     public ResponseEntity<?> handleResourceNotFoundException(ResourceNotFoundException ex){
         log.warn("Resource not found: {}", ex.getMessage());
         return ResponseEntity.status(HttpStatus.NOT_FOUND)
                 .body("Error: " + ex.getMessage());
     }

     @ExceptionHandler(RuntimeException.class)
     public ResponseEntity<?> handleRuntimeException(RuntimeException ex){
         log.error("Unexpected error: {}", ex.getMessage(), ex);
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                 .body("Error: An unexpected error occurred");
     }
}
