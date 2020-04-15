package be.sandervl.invoicepdf.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.internet.MimeMessage;
import java.io.File;

@Component
@RequiredArgsConstructor
public class EmailService {
    public static final String MAIL_ORIGINATOR = "noreply@sandervl.be";
    private final JavaMailSender emailSender;

    @SneakyThrows
    public void sendMessageWithAttachment(String to, String subject, String text, String pathToAttachment, String attachmentName) {
        MimeMessage message = emailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, true);
        helper.setFrom(MAIL_ORIGINATOR);

        FileSystemResource file = new FileSystemResource(new File(pathToAttachment));
        helper.addAttachment(attachmentName, file, MediaType.APPLICATION_PDF_VALUE);

        emailSender.send(message);
    }
}
