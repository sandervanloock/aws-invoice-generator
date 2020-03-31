package be.sandervl.invoicepdf.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCurrencyExchange {

    @Test
    void convertCurrency() throws JsonProcessingException {
        Double convertedCurrency = new CurrencyExchange().convertCurrency(1D, "EUR", "USD");
        assertTrue(convertedCurrency < 1);
    }
}