package com.lms.progress.aggregate;

import com.lms.progress.entity.ProgressStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Business rules shared by the aggregate (write side) and the projector (read side), so both always
 * compute percentage and status identically.
 */
public final class ProgressRules {

    public static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private ProgressRules() { }

    /** percentage = completedModules / totalModules x 100, 2 decimals, capped at 100. */
    public static BigDecimal percentage(int completedModules, int totalModules) {
        if (totalModules <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal value = BigDecimal.valueOf(completedModules)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalModules), 2, RoundingMode.HALF_UP);
        return value.compareTo(HUNDRED) > 0 ? HUNDRED : value;
    }

    public static boolean isFullyCompleted(BigDecimal percentage) {
        return percentage != null && percentage.compareTo(HUNDRED) == 0;
    }

    /** Status of a progress that is neither FAILED nor COMPLETED. */
    public static ProgressStatus activeStatus(BigDecimal percentage, boolean assessmentPassed, boolean anyActivity) {
        if (isFullyCompleted(percentage) && !assessmentPassed) {
            return ProgressStatus.AWAITING_ASSESSMENT;
        }
        return anyActivity ? ProgressStatus.IN_PROGRESS : ProgressStatus.NOT_STARTED;
    }

    /** Larger of two scores, null-safe. */
    public static BigDecimal max(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return candidate.compareTo(current) > 0 ? candidate : current;
    }
}
