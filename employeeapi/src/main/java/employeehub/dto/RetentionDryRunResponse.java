package employeehub.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RetentionDryRunResponse {

    private final int retentionDays;
    private final LocalDateTime cutoff;
    private final long eligibleRecords;
    private final boolean destructive;

    public RetentionDryRunResponse(int retentionDays, LocalDateTime cutoff, long eligibleRecords) {
        this.retentionDays = retentionDays;
        this.cutoff = cutoff;
        this.eligibleRecords = eligibleRecords;
        this.destructive = false;
    }
}
