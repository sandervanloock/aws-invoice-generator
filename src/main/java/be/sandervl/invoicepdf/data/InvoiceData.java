package be.sandervl.invoicepdf.data;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class InvoiceData {
    private int invoiceNumber;
    private LocalDate created;
    private LocalDate dueDate;
    private String companyName;
    private String contactPerson;
    private String contactEmail;

    public static InvoiceData getDefaultInvoiceData() {
        return InvoiceData.builder()
                .invoiceNumber(LocalDate.now().hashCode())
                .created(LocalDate.now())
                .dueDate(LocalDate.now().plusMonths(1))
                .companyName("Kranzenzo")
                .contactPerson("Annemie Rousseau")
                .contactEmail("lierserulez@hotmail.com")
                .build();
    }
}
