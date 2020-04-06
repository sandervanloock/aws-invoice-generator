package be.sandervl.invoicepdf.services;

import be.sandervl.invoicepdf.data.DateRange;
import be.sandervl.invoicepdf.data.Invoice;
import be.sandervl.invoicepdf.data.InvoiceData;
import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AwsInvoiceService {

    public static final double TAX_PERCENTACE = 0.21;

    private final AWSCostExplorer costExplorer;
    private final CurrencyExchange currencyExchange;

    public Invoice getInvoice(String currency, DateRange dateRange) {
        Invoice invoice = getInvoice(dateRange);
        return currencyExchange.adaptInvoiceForCurrency(invoice, currency);
    }

    public Invoice getInvoice(String currency) {
        return getInvoice(currency, DateRange.getLastMonthDateRange());
    }

    private Invoice getInvoice(DateRange dateRange) {
        InvoiceData invoiceData = InvoiceData.builder()
                .invoiceNumber(1)
                .created(LocalDate.now())
                .dueDate(LocalDate.now().plusMonths(1))
                .companyName("Kranzenzo")
                .contactPerson("Annemie Rousseau")
                .contactEmail("lierserulez@hotmail.com")
                .build();
        DateInterval awsDatePeriod = new DateInterval();
        awsDatePeriod.setStart(dateRange.getStart().format(DateTimeFormatter.ISO_DATE));
        awsDatePeriod.setEnd(dateRange.getEnd().format(DateTimeFormatter.ISO_DATE));
        GetCostAndUsageRequest request = buildCostAndUsageRequest("kranzenzo", awsDatePeriod);
        GetCostAndUsageResult costAndUsage = costExplorer.getCostAndUsage(request);
        LOG.debug("Got result {}", costAndUsage);
        Map<String, MetricValue> items = costAndUsage.getResultsByTime()
                .stream()
                .map(this::getMetricValuePerService)
                .flatMap(e -> e.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, mergeMetricValue()));
        Invoice invoice = new Invoice(items, invoiceData);
        addTaxMetricToEntries(invoice);
        return invoice;
    }

    private void addTaxMetricToEntries(Invoice invoice) {
        MetricValue taxMetric = new MetricValue();
        taxMetric.setAmount(Double.parseDouble(invoice.getTotal().getAmount()) * TAX_PERCENTACE + "");
        taxMetric.setUnit("USD");
        invoice.getItems().put("TAX", taxMetric);
    }

    private Map<String, MetricValue> getMetricValuePerService(ResultByTime resultByTime) {
        return resultByTime.getGroups()
                .stream()
                .filter(e -> Double.parseDouble(e.getMetrics().get("AmortizedCost").getAmount()) != 0)
                .collect(Collectors.toMap(e -> e.getKeys().get(0), e -> e.getMetrics().get("AmortizedCost"), mergeMetricValue()));
    }

    private BinaryOperator<MetricValue> mergeMetricValue() {
        return (a, b) -> {
            LOG.debug("merge {}, {}", a, b);
            MetricValue result = new MetricValue();
            result.setUnit(a.getUnit());
            result.setAmount(String.valueOf(Double.parseDouble(a.getAmount()) + Double.parseDouble(b.getAmount())));
            return result;
        };
    }

    private GetCostAndUsageRequest buildCostAndUsageRequest(String applicationFilter, DateInterval timePeriod) {
        GetCostAndUsageRequest request = new GetCostAndUsageRequest();
        Expression filter = new Expression();
        TagValues tags = new TagValues();
        tags.setKey("application");
        tags.setValues(Collections.singletonList(applicationFilter));
        filter.setTags(tags);
        request.setFilter(filter);
        request.setGranularity("MONTHLY");
        request.setTimePeriod(timePeriod);
        request.setMetrics(List.of("AmortizedCost"));
        GroupDefinition serviceGroupDefinition = new GroupDefinition();
        serviceGroupDefinition.setKey("SERVICE");
        serviceGroupDefinition.setType("DIMENSION");
        request.setGroupBy(List.of(serviceGroupDefinition));
        LOG.debug("AWS request issued {}", request);
        return request;
    }
}
