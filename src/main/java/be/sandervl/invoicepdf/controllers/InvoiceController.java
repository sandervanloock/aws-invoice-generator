package be.sandervl.invoicepdf.controllers;

import be.sandervl.invoicepdf.data.DateRange;
import be.sandervl.invoicepdf.data.Invoice;
import be.sandervl.invoicepdf.services.AwsInvoiceService;
import be.sandervl.invoicepdf.services.EmailService;
import be.sandervl.invoicepdf.services.PdfCreator;
import be.sandervl.invoicepdf.services.ThymeleafEmailComposer;
import com.itextpdf.text.DocumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
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
import java.util.Locale;

@Controller
@Profile("!lambda")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private final AwsInvoiceService awsInvoiceService;
    private final PdfCreator pdfCreator;
    private final EmailService emailService;
    private final ThymeleafEmailComposer thymeleafEmailComposer;
    private final MessageSource messageSource;

    public static void setInvoiceDataOnModel(ModelMap modelMap, Invoice awsInvoice) {
        modelMap.addAttribute("invoiceItems", awsInvoice.getItems());
        modelMap.addAttribute("invoiceTotal", awsInvoice.getTotal());
        modelMap.addAttribute("invoicePeriod", DateRange.getLastMonthDateRange());
        modelMap.addAttribute("invoiceData", awsInvoice.getInvoiceData());
    }

    @GetMapping(path = "/invoice", produces = MediaType.TEXT_HTML_VALUE)
    public String getInvoice(ModelMap modelMap) {
        setInvoiceDataOnModel(modelMap, awsInvoiceService.getInvoice("EUR"));
        return "invoice";
    }

    @GetMapping(path = "/invoicePdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<InputStreamResource> getInvoicePdf(ModelMap modelMap) throws IOException, DocumentException {
        setInvoiceDataOnModel(modelMap, awsInvoiceService.getInvoice("EUR"));
        File pdf = pdfCreator.createPdf(modelMap);
        InputStreamResource inputStreamResource = new InputStreamResource(new FileInputStream(pdf));
        return ResponseEntity.ok(inputStreamResource);
    }

    @GetMapping(path = "/invoiceMail")
    public ResponseEntity<String> getInvoiceByMail(ModelMap modelMap) throws IOException, DocumentException {
        Invoice invoice = awsInvoiceService.getInvoice("EUR");
        LOG.debug("Created invoice {}", invoice);
        setInvoiceDataOnModel(modelMap, invoice);

        File pdf = pdfCreator.createPdf(modelMap);

        Locale locale = Locale.ENGLISH;
        String mailSubject = messageSource.getMessage("email.subject", null, locale);
        String mailBody = thymeleafEmailComposer.getContent("mail/invoice", locale, modelMap);
        emailService.sendMessageWithAttachment(
                invoice.getInvoiceData().getContactEmail(),
                mailSubject,
                mailBody,
                pdf.getAbsolutePath());

        LOG.debug("Ended invoice mailings");
        return ResponseEntity.ok("OK");
    }

}
