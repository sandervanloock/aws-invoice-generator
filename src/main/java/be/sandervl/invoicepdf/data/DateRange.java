package be.sandervl.invoicepdf.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DateRange {
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start, end;

    public static DateRange getLastMonthDateRange() {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        int numberOfDaysInLastMonth = lastMonth.getMonth().length(lastMonth.isLeapYear());
        LocalDate start = lastMonth.withDayOfMonth(1);
        LocalDate end = lastMonth.withDayOfMonth(numberOfDaysInLastMonth);
        return new DateRange(start, end);
    }
}
