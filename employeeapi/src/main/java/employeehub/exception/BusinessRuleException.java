package employeehub.exception;

/**
 * Thrown when a request conflicts with the current state of a resource
 * (duplicate, already-approved, insufficient balance, wrong state transition).
 * Maps to HTTP 409 Conflict.
 *
 * <p>This type exists so that a genuine business-rule conflict is distinguishable
 * from an {@link IllegalArgumentException}/{@link IllegalStateException} thrown by
 * Spring or a third-party library. Without it, a framework bug would be reported
 * to the client as a 400/409 and never investigated.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
