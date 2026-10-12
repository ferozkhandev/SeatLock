package com.ferozkhandev.seatlock.catalog_service.utils;

public final class ProblemPathsUtil {

    private ProblemPathsUtil() {
        throw new UnsupportedOperationException("Object of util class cannot be created");
    }

    private static final String PROBLEM_PATH = "/problems";
    public static final String VALIDATION_FAILED = PROBLEM_PATH + "/validation-failed";
    public static final String EVENT_NOT_FOUND = PROBLEM_PATH + "/event-not-found";
    public static final String INVALID_REQUEST_BODY = PROBLEM_PATH + "/invalid-request-body";
    public static final String VENUE_NOT_FOUND = PROBLEM_PATH + "/venue-not-found";
    public static final String MISSING_REQUEST_HEADER = PROBLEM_PATH + "/missing-request-header";
    public static final String INVALID_ARGUMENT_TYPE = PROBLEM_PATH + "/invalid-argument-type";
    public static final String INTERNAL_SERVER_ERROR = PROBLEM_PATH + "/internal-server-error";
}
