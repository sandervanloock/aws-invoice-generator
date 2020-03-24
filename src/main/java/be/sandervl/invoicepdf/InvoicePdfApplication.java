package be.sandervl.invoicepdf;

import be.sandervl.invoicepdf.controllers.InvoiceController;
import be.sandervl.invoicepdf.data.Invoice;
import be.sandervl.invoicepdf.services.AwsInvoiceService;
import be.sandervl.invoicepdf.services.CurrencyExchange;
import be.sandervl.invoicepdf.services.EmailService;
import be.sandervl.invoicepdf.services.PdfCreator;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;

import java.io.File;

@SpringBootApplication
public class InvoicePdfApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(InvoicePdfApplication.class, args);
    }
}

@Profile("lambda")
@Slf4j
@Component
@RequiredArgsConstructor
class LambaExcecution implements
        ApplicationListener<ContextRefreshedEvent> {
    private final AwsInvoiceService awsInvoiceService;
    private final CurrencyExchange currencyExchange;
    private final PdfCreator pdfCreator;
    private final EmailService emailService;

    @SneakyThrows
    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        LOG.debug("Started application {}", contextRefreshedEvent);
        Invoice invoice = currencyExchange.adaptInvoiceForCurrency(awsInvoiceService.getInvoice(), "EUR");

        LOG.debug("Created invoice {}", invoice);
        ModelMap modelMap = new ModelMap();
        InvoiceController.setInvoiceDataOnModel(modelMap, invoice);
        File pdf = pdfCreator.createPdf(modelMap);
        emailService.sendMessageWithAttachment("lierserulez@hotmail.com", "invoice ready", "invoice can be found in attachment", pdf.getAbsolutePath());

        LOG.debug("Ended invoice mailings");
    }
}

