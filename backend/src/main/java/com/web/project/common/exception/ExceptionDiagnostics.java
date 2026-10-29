package com.web.project.common.exception;

/** Formats exception types and stack frames without potentially sensitive messages. */
public final class ExceptionDiagnostics {

    private ExceptionDiagnostics() {
    }

    public static String withoutMessages(Throwable exception) {
        StringBuilder diagnostics = new StringBuilder();
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            diagnostics.append(cause.getClass().getName()).append('\n');
            for (StackTraceElement frame : cause.getStackTrace()) {
                diagnostics.append("\tat ").append(frame).append('\n');
            }
        }
        return diagnostics.toString();
    }
}
