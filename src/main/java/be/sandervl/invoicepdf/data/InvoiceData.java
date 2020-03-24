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
}
