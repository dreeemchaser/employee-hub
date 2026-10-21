package employeehub.domain.enums;

/**
 * Controls which roles may access a document in addition to its owner.
 */
public enum DocumentAccessLevel {
    STANDARD,
    CONFIDENTIAL,
    PAYROLL_ONLY
}
