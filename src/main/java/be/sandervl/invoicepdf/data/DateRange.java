package be.sandervl.invoicepdf.data;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class DateRange {
    private LocalDate start, end;

    public static DateRange getLastMonthDateRange() {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        int numberOfDaysInLastMonth = lastMonth.getMonth().length(lastMonth.isLeapYear());
        LocalDate start = lastMonth.withDayOfMonth(1);
        LocalDate end = lastMonth.withDayOfMonth(numberOfDaysInLastMonth);
        return new DateRange(start, end);
    }
}
