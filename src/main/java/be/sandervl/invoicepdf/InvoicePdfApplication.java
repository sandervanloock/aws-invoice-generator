package be.sandervl.invoicepdf;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.monitoring.ProfileCsmConfigurationProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import com.amazonaws.services.costexplorer.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.DocumentException;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@SpringBootApplication
public class InvoicePdfApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoicePdfApplication.class, args);
    }

}

@Configuration
class AwsConfiguration {

    public static final String AWS_USER_ACCOUNT = "pdf-invoice";

    @Bean
    @Profile("prod")
    public AWSCostExplorer awsCostExplorer() {
        return AWSCostExplorerClientBuilder.defaultClient();
    }

    @Bean
    @Profile("dev")
    public AWSCostExplorer awsCostExplorerDev() {
        return AWSCostExplorerClientBuilder.standard()
                .withCredentials(new ProfileCredentialsProvider(AWS_USER_ACCOUNT))
                .withRegion(Regions.EU_WEST_1)
                .withClientSideMonitoringConfigurationProvider(new ProfileCsmConfigurationProvider(AWS_USER_ACCOUNT))
                .build();
    }
}

@Controller
class InvoiceController {
    private final AwsInvoiceService awsInvoiceService;
    private final CurrencyExchange currencyExchange;
    private final PdfCreator pdfCreator;

    InvoiceController(AwsInvoiceService awsInvoiceService, CurrencyExchange currencyExchange, PdfCreator pdfCreator) {
        this.awsInvoiceService = awsInvoiceService;
        this.currencyExchange = currencyExchange;
        this.pdfCreator = pdfCreator;
    }

    @GetMapping(path = "/invoice", produces = MediaType.TEXT_HTML_VALUE)
    public String getInvoice(ModelMap modelMap) {
        setInvoiceDataOnModel(modelMap);
        return "invoice";
    }

    @GetMapping(path = "/invoicePdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<InputStreamResource> getInvoicePdf(ModelMap modelMap) throws IOException, DocumentException {
        setInvoiceDataOnModel(modelMap);
        File pdf = pdfCreator.createPdf(modelMap);
        InputStreamResource inputStreamResource = new InputStreamResource(new FileInputStream(pdf));
        return ResponseEntity.ok(inputStreamResource);
    }

    private void setInvoiceDataOnModel(ModelMap modelMap) {
        MetricValue awsInvoice = getTotalAwsCostMetric();
        List<MetricValue> items = List.of(awsInvoice);
        MetricValue totalInvoice = calculateTotalMetric(items);
        modelMap.addAttribute("invoiceItems", items);
        modelMap.addAttribute("invoiceTotal", totalInvoice);
        modelMap.addAttribute("invoiceData", InvoiceData.builder()
                .invoiceNumber(1)
                .created(LocalDate.now())
                .dueDate(LocalDate.now().plusMonths(1))
                .companyName("Kranzenzo")
                .contactPerson("Annemie Rousseau")
                .contactEmail("annemie.rousseau@telenet.be")
                .build());
    }

    private MetricValue calculateTotalMetric(List<MetricValue> items) {
        MetricValue initialValue = new MetricValue();
        initialValue.setAmount("0");
        initialValue.setUnit("EUR");
        return items.stream().reduce(initialValue, (a, b) -> {
            MetricValue sum = new MetricValue();
            sum.setAmount(String.valueOf(Double.parseDouble(a.getAmount()) + Double.parseDouble(b.getAmount())));
            sum.setUnit(a.getUnit());
            return sum;
        });
    }

    private MetricValue getTotalAwsCostMetric() {
        MetricValue awsInvoice = awsInvoiceService.getInvoice();
        Double convertedCurrency = currencyExchange.convertCurrency(Double.valueOf(awsInvoice.getAmount()), awsInvoice.getUnit(), "EUR");
        awsInvoice.setAmount(String.valueOf(convertedCurrency));
        awsInvoice.setUnit("EUR");
        return awsInvoice;
    }

}

@Data
@Builder
class InvoiceData {
    private int invoiceNumber;
    private LocalDate created;
    private LocalDate dueDate;
    private String companyName;
    private String contactPerson;
    private String contactEmail;
}

@Service
class PdfCreator {
    private final TemplateEngine templateEngine;

    PdfCreator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public File createPdf(Map<String, Object> data) throws IOException, DocumentException {
        String htmlInvoice = templateEngine.process("invoice", new Context(Locale.getDefault(), data));
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(htmlInvoice);
        renderer.layout();
        File outputFile = new File("example-pdf.pdf");
        try (OutputStream os = new FileOutputStream(outputFile)) {
            renderer.createPDF(os);
            os.flush();
        }
        return outputFile;
    }
}

@Service
class CurrencyExchange {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    public CurrencyExchange() {
        restTemplate = new RestTemplateBuilder().build();
        objectMapper = new ObjectMapper();
    }

    @SneakyThrows
    Double convertCurrency(Double inputValue, String fromCurrency, String toCurrency) {
        ResponseEntity<String> restResponse = restTemplate.exchange("https://api.exchangeratesapi.io/latest?base=" + fromCurrency, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        double dollarValue = Optional.ofNullable(objectMapper.readTree(restResponse.getBody()))
                .flatMap(body -> Optional.ofNullable(body.get("rates")))
                .flatMap(rates -> Optional.ofNullable(rates.get(toCurrency)))
                .map(JsonNode::asDouble)
                .orElseThrow();
        double convertedValue = inputValue / dollarValue;
        return Math.round(convertedValue * 100.0) / 100.0;
    }
}

@Service
@Slf4j
class AwsInvoiceService {
    private final AWSCostExplorer costExplorer;

    AwsInvoiceService(AWSCostExplorer costExplorer) {
        this.costExplorer = costExplorer;
    }

    MetricValue getInvoice() {
        GetCostAndUsageRequest request = buildCostAndUsageRequest("kranzenzo");
        GetCostAndUsageResult costAndUsage = costExplorer.getCostAndUsage(request);
        LOG.debug("Got result {}", costAndUsage);
        return costAndUsage.getResultsByTime()
                .stream()
                .map(ResultByTime::getTotal)
                .flatMap(e -> e.values().stream())
                .findFirst()
                .orElse(new MetricValue());
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
        LOG.debug("AWS request issued {}", request);
        return request;
    }

    private DateInterval getTimePeriodForLastMonth() {
        DateInterval timePeriod = new DateInterval();
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        int numberOfDaysInLastMonth = lastMonth.getMonth().length(lastMonth.isLeapYear());
        timePeriod.setStart(lastMonth.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE));
        timePeriod.setEnd(lastMonth.withDayOfMonth(numberOfDaysInLastMonth).format(DateTimeFormatter.ISO_DATE));
        return timePeriod;
    }
}
