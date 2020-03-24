package be.sandervl.invoicepdf.services;

import com.itextpdf.text.DocumentException;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

@Service
public class PdfCreator {
    public static final Path TARGET_PDF = Paths.get("/tmp/example-pdf.pdf");
    private final TemplateEngine templateEngine;

    PdfCreator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public File createPdf(Map<String, Object> data) throws IOException, DocumentException {
        String htmlInvoice = templateEngine.process("invoice", new Context(Locale.getDefault(), data));
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(htmlInvoice);
        renderer.layout();

        File outputFile = createOutputPdfFile();

        try (OutputStream os = new FileOutputStream(outputFile)) {
            renderer.createPDF(os);
            os.flush();
        }
        return outputFile;
    }

    private File createOutputPdfFile() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        File inputFile = new File(classLoader.getResource("example-pdf.pdf").getFile());

        Files.deleteIfExists(TARGET_PDF);
        Path tmpPf = Files.copy(inputFile.toPath(), TARGET_PDF);
        return tmpPf.toFile();
    }
}
