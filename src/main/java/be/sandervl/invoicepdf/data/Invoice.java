package be.sandervl.invoicepdf.data;

import com.amazonaws.services.costexplorer.model.MetricValue;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@AllArgsConstructor
@Data
public class Invoice {
    private Map<String, MetricValue> items;
    private DateRange dateRange;
    private InvoiceData invoiceData;

    public MetricValue getTotal() {
        MetricValue initialValue = new MetricValue();
        initialValue.setAmount("0");
        initialValue.setUnit("EUR");
        return items.values().stream().reduce(initialValue, (a, b) -> {
            MetricValue sum = new MetricValue();
            sum.setAmount(String.valueOf(Double.parseDouble(a.getAmount()) + Double.parseDouble(b.getAmount())));
            sum.setUnit(a.getUnit());
            return sum;
        });
    }
}
