package be.sandervl.invoicepdf.services;

import be.sandervl.invoicepdf.data.Invoice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;

@Service
public class CurrencyExchange {

    private static final Map<String, Double> conversionCurrencyCache = new HashMap<>();
    private static final BinaryOperator<String> conversionCurrencyCacheKeyGenerator =
            (from, to) -> String.format("FROM:%s-TO:%s", from, to);

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    public CurrencyExchange() {
        restTemplate = new RestTemplateBuilder().build();
        objectMapper = new ObjectMapper();
    }

    public Double convertCurrency(Double inputValue, String fromCurrency, String toCurrency) {
        String cacheKey = conversionCurrencyCacheKeyGenerator.apply(fromCurrency, toCurrency);
        Double conversionRate = conversionCurrencyCache.computeIfAbsent(cacheKey,
                k -> getConversionRate(fromCurrency, toCurrency));
        conversionCurrencyCache.put(cacheKey, conversionRate);

        double convertedValue = inputValue / conversionRate;
        return Math.round(convertedValue * 100.0) / 100.0;
    }

    public Invoice adaptInvoiceForCurrency(Invoice awsInvoiceEntries, String currency) {
        awsInvoiceEntries.getItems().values().forEach(awsInvoice -> {
            Double convertedCurrency = convertCurrency(Double.valueOf(awsInvoice.getAmount()), awsInvoice.getUnit(), currency);
            awsInvoice.setAmount(String.valueOf(convertedCurrency));
            awsInvoice.setUnit(currency);
        });
        return awsInvoiceEntries;
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
