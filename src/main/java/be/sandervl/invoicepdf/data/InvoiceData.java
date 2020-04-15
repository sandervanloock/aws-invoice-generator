package be.sandervl.invoicepdf.data;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class InvoiceData {
    private String number;
    private String name;
    private LocalDate created;
    private LocalDate dueDate;
    private String companyName;
    private String contactPerson;
    private String contactEmail;

    public static InvoiceData getDefaultInvoiceData() {
        return InvoiceData.builder()
                .number(createInvoiceNumber())
                .created(LocalDate.now())
                .dueDate(LocalDate.now().plusMonths(1))
                .companyName("Kranzenzo")
                .contactPerson("Annemie Rousseau")
                .contactEmail("lierserulez@hotmail.com")
                .build();
    }

    private static String createInvoiceNumber() {
        return "SANDERVL_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public String getFileName() {
        return number.toUpperCase().replaceAll("\\W+", "_");
    }
}
