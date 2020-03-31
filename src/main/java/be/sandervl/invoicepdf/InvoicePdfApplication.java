package be.sandervl.invoicepdf;

import be.sandervl.invoicepdf.controllers.InvoiceController;
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
class LambaExcecution implements ApplicationListener<ContextRefreshedEvent> {

    private final InvoiceController invoiceController;

    @SneakyThrows
    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        ModelMap modelMap = new ModelMap();
        LOG.debug("Started application {}", contextRefreshedEvent);
        invoiceController.getInvoiceByMail(modelMap);
        LOG.debug("Ended invoice mailings");
    }
}

