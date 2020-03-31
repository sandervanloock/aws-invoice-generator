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
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AwsInvoiceService {

    public static final double TAX_PERCENTACE = 0.21;

    private final AWSCostExplorer costExplorer;
    private final CurrencyExchange currencyExchange;

    public Invoice getInvoice() {
        InvoiceData invoiceData = InvoiceData.builder()
                .invoiceNumber(1)
                .created(LocalDate.now())
                .dueDate(LocalDate.now().plusMonths(1))
                .companyName("Kranzenzo")
                .contactPerson("Annemie Rousseau")
                .contactEmail("lierserulez@hotmail.com")
                .build();

        GetCostAndUsageRequest request = buildCostAndUsageRequest("kranzenzo");
        GetCostAndUsageResult costAndUsage = costExplorer.getCostAndUsage(request);
        LOG.debug("Got result {}", costAndUsage);
        Map<String, MetricValue> items = costAndUsage.getResultsByTime().stream()
                .map(this::getMetricValuePerService)
                .flatMap(e -> e.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Invoice invoice = new Invoice(items, invoiceData);
        addTaxMetricToEntries(invoice);
        return invoice;
    }

    public Invoice getInvoice(String currency) {
        return currencyExchange.adaptInvoiceForCurrency(getInvoice(), currency);
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
                .collect(Collectors.toMap(e -> e.getKeys().get(0), e -> e.getMetrics().get("AmortizedCost")));
    }

    private GetCostAndUsageRequest buildCostAndUsageRequest(String applicationFilter) {
        GetCostAndUsageRequest request = new GetCostAndUsageRequest();
        Expression filter = new Expression();
        TagValues tags = new TagValues();
        tags.setKey("application");
        tags.setValues(Collections.singletonList(applicationFilter));
        filter.setTags(tags);
        request.setFilter(filter);
        request.setGranularity("MONTHLY");
        DateInterval timePeriod = getTimePeriodForLastMonth();
        request.setTimePeriod(timePeriod);
        request.setMetrics(List.of("AmortizedCost"));
        GroupDefinition serviceGroupDefinition = new GroupDefinition();
        serviceGroupDefinition.setKey("SERVICE");
        serviceGroupDefinition.setType("DIMENSION");
        request.setGroupBy(List.of(serviceGroupDefinition));
        LOG.debug("AWS request issued {}", request);
        return request;
    }

    private DateInterval getTimePeriodForLastMonth() {
        DateRange dateRange = DateRange.getLastMonthDateRange();
        DateInterval awsDatePeriod = new DateInterval();
        awsDatePeriod.setStart(dateRange.getStart().format(DateTimeFormatter.ISO_DATE));
        awsDatePeriod.setEnd(dateRange.getEnd().format(DateTimeFormatter.ISO_DATE));
        return awsDatePeriod;
    }

}
