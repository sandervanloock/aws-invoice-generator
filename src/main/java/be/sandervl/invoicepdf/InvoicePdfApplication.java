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
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.mail.internet.MimeMessage;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@SpringBootApplication
public class InvoicePdfApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoicePdfApplication.class, args);
    }

}

@Component
@RequiredArgsConstructor
class EmailService {
    private final JavaMailSender emailSender;

    @SneakyThrows
    public void sendMessageWithAttachment(String to, String subject, String text, String pathToAttachment) {
        MimeMessage message = emailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text);

        FileSystemResource file
                = new FileSystemResource(new File(pathToAttachment));
        helper.addAttachment("Invoice", file, MediaType.APPLICATION_PDF_VALUE);

        emailSender.send(message);
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
    public static final double TAX_PERCENTACE = 0.21;
    private final AwsInvoiceService awsInvoiceService;
    private final CurrencyExchange currencyExchange;
    private final PdfCreator pdfCreator;
    private final EmailService emailService;

    InvoiceController(AwsInvoiceService awsInvoiceService, CurrencyExchange currencyExchange, PdfCreator pdfCreator, EmailService emailService) {
        this.awsInvoiceService = awsInvoiceService;
        this.currencyExchange = currencyExchange;
        this.pdfCreator = pdfCreator;
        this.emailService = emailService;
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

    @GetMapping(path = "/invoiceMail", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<String> getInvoiceByMail(ModelMap modelMap) throws IOException, DocumentException {
        setInvoiceDataOnModel(modelMap);
        File pdf = pdfCreator.createPdf(modelMap);
        emailService.sendMessageWithAttachment("lierserulez@hotmail.com", "invoice ready", "invoice can be found in attachment", pdf.getAbsolutePath());
        return ResponseEntity.ok("OK");
    }

    private void setInvoiceDataOnModel(ModelMap modelMap) {
        Map<String, MetricValue> awsInvoice = getAwsCostsForCurrency("EUR");
        MetricValue totalInvoice = calculateTotalMetric(awsInvoice.values());
        modelMap.addAttribute("invoiceItems", awsInvoice);
        modelMap.addAttribute("invoiceTotal", totalInvoice);
        modelMap.addAttribute("invoicePeriod", DateRange.getLastMonthDateRange());
        modelMap.addAttribute("invoiceData", InvoiceData.builder()
                .invoiceNumber(1)
                .created(LocalDate.now())
                .dueDate(LocalDate.now().plusMonths(1))
                .companyName("Kranzenzo")
                .contactPerson("Annemie Rousseau")
                .contactEmail("annemie.rousseau@telenet.be")
                .build());
    }

    private MetricValue calculateTotalMetric(Collection<MetricValue> items) {
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

    private Map<String, MetricValue> getAwsCostsForCurrency(String currency) {
        Map<String, MetricValue> awsInvoiceEntries = awsInvoiceService.getInvoice();
        addTaxMetricToEntries(awsInvoiceEntries);
        awsInvoiceEntries.values().forEach(awsInvoice -> {
            Double convertedCurrency = currencyExchange.convertCurrency(Double.valueOf(awsInvoice.getAmount()), awsInvoice.getUnit(), currency);
            awsInvoice.setAmount(String.valueOf(convertedCurrency));
            awsInvoice.setUnit(currency);
        });
        return awsInvoiceEntries;
    }

    private void addTaxMetricToEntries(Map<String, MetricValue> awsInvoiceEntries) {
        MetricValue taxMetric = new MetricValue();
        taxMetric.setAmount(Double.parseDouble(calculateTotalMetric(awsInvoiceEntries.values()).getAmount()) * TAX_PERCENTACE + "");
        taxMetric.setUnit("USD");
        awsInvoiceEntries.put("TAX", taxMetric);
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

    private static final Map<String, Double> conversionCurrencyCache = new HashMap<>();
    private static final BinaryOperator<String> conversionCurrencyCacheKeyGenerator =
            (from, to) -> String.format("FROM:%s-TO:%s", from, to);

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    public CurrencyExchange() {
        restTemplate = new RestTemplateBuilder().build();
        objectMapper = new ObjectMapper();
    }

    Double convertCurrency(Double inputValue, String fromCurrency, String toCurrency) {
        String cacheKey = conversionCurrencyCacheKeyGenerator.apply(fromCurrency, toCurrency);
        Double conversionRate = conversionCurrencyCache.computeIfAbsent(cacheKey,
                k -> getConversionRate(fromCurrency, toCurrency));
        conversionCurrencyCache.put(cacheKey, conversionRate);

        double convertedValue = inputValue / conversionRate;
        return Math.round(convertedValue * 100.0) / 100.0;
    }

    @SneakyThrows
    private Double getConversionRate(String fromCurrency, String toCurrency) {
        ResponseEntity<String> restResponse = restTemplate.exchange("https://api.exchangeratesapi.io/latest?base=" + fromCurrency, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        return Optional.ofNullable(objectMapper.readTree(restResponse.getBody()))
                .flatMap(body -> Optional.ofNullable(body.get("rates")))
                .flatMap(rates -> Optional.ofNullable(rates.get(toCurrency)))
                .map(JsonNode::asDouble)
                .orElseThrow();
    }
}

@Service
@Slf4j
class AwsInvoiceService {
    private final AWSCostExplorer costExplorer;

    AwsInvoiceService(AWSCostExplorer costExplorer) {
        this.costExplorer = costExplorer;
    }

    Map<String, MetricValue> getInvoice() {
        GetCostAndUsageRequest request = buildCostAndUsageRequest("kranzenzo");
        GetCostAndUsageResult costAndUsage = costExplorer.getCostAndUsage(request);
        LOG.debug("Got result {}", costAndUsage);
        return costAndUsage.getResultsByTime().stream()
                .map(this::getMetricValuePerService)
                .flatMap(e -> e.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

@Data
@AllArgsConstructor
class DateRange {
    private LocalDate start, end;

    public static DateRange getLastMonthDateRange() {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        int numberOfDaysInLastMonth = lastMonth.getMonth().length(lastMonth.isLeapYear());
        LocalDate start = lastMonth.withDayOfMonth(1);
        LocalDate end = lastMonth.withDayOfMonth(numberOfDaysInLastMonth);
        return new DateRange(start, end);
    }
}

