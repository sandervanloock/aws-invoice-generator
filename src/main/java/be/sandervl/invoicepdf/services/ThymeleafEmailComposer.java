package be.sandervl.invoicepdf.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ThymeleafEmailComposer {

    private final SpringTemplateEngine templateEngine;

    public String getContent(String templateName, Locale locale, ModelMap modelMap) {
        Context context = new Context(locale);
        context.setVariables(modelMap);
        return templateEngine.process(templateName, context);
    }
}
