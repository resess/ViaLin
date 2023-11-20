package ca.ubc.ece.resess.taint.dynamic.vialin;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AnalysisLogger {


    /**
     * Private constructor to prevent instantiation.
     */
    private AnalysisLogger() {}

    /**
     * Logs a message if the specified condition for logging is true.
     *
     * @param shouldLog A boolean value indicating whether the log message should be printed.
     * @param format    A format string as described in {@link java.util.Formatter} syntax.
     * @param args      Arguments referenced by the format specifiers in the format string.
     *                  If there are more arguments than format specifiers, the extra arguments are ignored.
     *                  The number of arguments is variable and may be zero.
     */

    public static void log(boolean shouldLog, String format, Object... args) {
        if (shouldLog) {
            System.out.format(format, args);
        }
    }

    /**
     * Converts the current thread's stack trace to a formatted string.
     * This method skips the first two stack trace elements, which typically
     * correspond to the method invocation itself and the method that called it.
     *
     * @return A formatted string containing the stack trace information.
     */
    public static String stackTraceToString() {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

        // Skip the first two elements as they are usually related to this method and its caller
        for (int i = 2; i < stackTraceElements.length; i++) {
            sb.append("    "); // Indentation for better readability
            sb.append(stackTraceElements[i].toString());
            sb.append("\n");
        }

        return sb.toString();
    }
}
