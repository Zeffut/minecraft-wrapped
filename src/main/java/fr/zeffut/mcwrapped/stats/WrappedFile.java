package fr.zeffut.mcwrapped.stats;

import java.time.YearMonth;

public record WrappedFile(
        YearMonth month,
        MonthlyDelta delta,
        boolean consumed
) {
    public WrappedFile asConsumed() {
        return new WrappedFile(month, delta, true);
    }
}
