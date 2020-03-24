package be.sandervl.invoicepdf.controllers;

import be.sandervl.invoicepdf.data.DateRange;
import be.sandervl.invoicepdf.data.Invoice;
import be.sandervl.invoicepdf.data.InvoiceData;
import be.sandervl.invoicepdf.services.AwsInvoiceService;
import be.sandervl.invoicepdf.services.CurrencyExchange;
import be.sandervl.invoicepdf.services.EmailService;
import be.sandervl.invoicepdf.services.PdfCreator;
import com.itextpdf.text.DocumentException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;

@Controller
@Profile("!lambda")
@RequiredArgsConstructor
public class InvoiceController {

    private final AwsInvoiceService awsInvoiceService;
    private final CurrencyExchange currencyExchange;
    private final PdfCreator pdfCreator;
    private final EmailService emailService;

    public static void setInvoiceDataOnModel(ModelMap modelMap, Invoice awsInvoice) {
        modelMap.addAttribute("invoiceItems", awsInvoice.getItems());
        modelMap.addAttribute("invoiceTotal", awsInvoice.getTotal());
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

    @GetMapping(path = "/invoice", produces = MediaType.TEXT_HTML_VALUE)
    public String getInvoice(ModelMap modelMap) {
        setInvoiceDataOnModel(modelMap, currencyExchange.adaptInvoiceForCurrency(awsInvoiceService.getInvoice(), "EUR"));
        return "invoice";
    }

    @GetMapping(path = "/invoicePdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<InputStreamResource> getInvoicePdf(ModelMap modelMap) throws IOException, DocumentException {
        setInvoiceDataOnModel(modelMap, currencyExchange.adaptInvoiceForCurrency(awsInvoiceService.getInvoice(), "EUR"));
        File pdf = pdfCreator.createPdf(modelMap);
        InputStreamResource inputStreamResource = new InputStreamResource(new FileInputStream(pdf));
        return ResponseEntity.ok(inputStreamResource);
    }

    @GetMapping(path = "/invoiceMail", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<String> getInvoiceByMail(ModelMap modelMap) throws IOException, DocumentException {
        setInvoiceDataOnModel(modelMap, currencyExchange.adaptInvoiceForCurrency(awsInvoiceService.getInvoice(), "EUR"));
        File pdf = pdfCreator.createPdf(modelMap);
        emailService.sendMessageWithAttachment("lierserulez@hotmail.com", "invoice ready", "invoice can be found in attachment", pdf.getAbsolutePath());
        return ResponseEntity.ok("OK");
    }

}
