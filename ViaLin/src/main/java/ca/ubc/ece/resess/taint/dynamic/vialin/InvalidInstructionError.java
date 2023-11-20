package ca.ubc.ece.resess.taint.dynamic.vialin;

public class InvalidInstructionError extends Error {

    /**
     * Constructs a new error with the specified detail message.
     *
     * @param message The detail message.
     */
    public InvalidInstructionError(String message) {
        super(message);
    }

    /**
     * Constructs a new error with the specified detail message and cause.
     *
     * @param message The detail message.
     * @param cause   The cause.
     */
    public InvalidInstructionError(String message, Throwable cause) {
        super(message, cause);
    }
}
