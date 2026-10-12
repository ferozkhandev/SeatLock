package com.ferozkhandev.seatlock.catalog_service.exceptions;

import com.ferozkhandev.seatlock.catalog_service.exceptions.event.EventNotFoundException;
import com.ferozkhandev.seatlock.catalog_service.exceptions.event.VenueNotFoundException;
import com.ferozkhandev.seatlock.catalog_service.utils.ProblemPathsUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ProblemDetail handleEventNotFound(EventNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Event Not Found");
        problemDetail.setType(URI.create(ProblemPathsUtil.EVENT_NOT_FOUND));
        problemDetail.setProperty("code", "EVENT_NOT_FOUND");
        return problemDetail;
    }

    @ExceptionHandler(VenueNotFoundException.class)
    public ProblemDetail handleVenueNotFound(VenueNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Venue Not Found");
        problemDetail.setType(URI.create(ProblemPathsUtil.VENUE_NOT_FOUND));
        problemDetail.setProperty("code", "VENUE_NOT_FOUND");
        return problemDetail;
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problemDetail = ex.getBody();
        problemDetail.setTitle("Validation Error");
        problemDetail.setDetail("The request payload is not valid.");
        problemDetail.setProperty("code", "VALIDATION_FAILED");
        problemDetail.setType(URI.create(ProblemPathsUtil.VALIDATION_FAILED));
        List<Map<String, String>> errors = new ArrayList<>();
        for (var bindingResult : ex.getBindingResult().getFieldErrors()) {
            String message = bindingResult.getDefaultMessage();
            if (message == null) {
                message = "Invalid value";
            }
            Map<String, String> error = Map.of("field", bindingResult.getField(), "message", message);
            errors.add(error);
        }
        problemDetail.setProperty("errors", errors);

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problemDetail = createProblemDetail(
                ex, status, "The request body is not valid.", null, null, request);
        problemDetail.setTitle("Invalid Request Body");
        problemDetail.setDetail("The request body is not valid.");
        problemDetail.setType(URI.create(ProblemPathsUtil.INVALID_REQUEST_BODY));
        problemDetail.setProperty("code", "INVALID_REQUEST_BODY");
        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (!(ex instanceof MissingRequestHeaderException)) {
            return super.handleServletRequestBindingException(ex, headers, status, request);
        }
        ProblemDetail problemDetail = ex.getBody();
        problemDetail.setTitle("Missing Request Header");
        problemDetail.setDetail("A required request header is missing.");
        problemDetail.setType(URI.create(ProblemPathsUtil.MISSING_REQUEST_HEADER));
        problemDetail.setProperty("code", "MISSING_REQUEST_HEADER");
        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (!(ex instanceof MethodArgumentTypeMismatchException)) {
            return super.handleTypeMismatch(ex, headers, status, request);
        }
        ProblemDetail problemDetail = createProblemDetail(
                ex, status, "The argument type is invalid.", null, null, request);
        problemDetail.setTitle("Invalid Argument Type");
        problemDetail.setDetail("The argument type is invalid.");
        problemDetail.setType(URI.create(ProblemPathsUtil.INVALID_ARGUMENT_TYPE));
        problemDetail.setProperty("code", "INVALID_ARGUMENT_TYPE");
        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex) {
        logger.error("Unexpected request failure", ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create(ProblemPathsUtil.INTERNAL_SERVER_ERROR));
        problemDetail.setProperty("code", "INTERNAL_SERVER_ERROR");
        return problemDetail;
    }
}
