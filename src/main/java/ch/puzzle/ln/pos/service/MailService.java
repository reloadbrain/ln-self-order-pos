package ch.puzzle.ln.pos.service;

import ch.puzzle.ln.pos.config.ApplicationProperties;
import ch.puzzle.ln.pos.config.ApplicationProperties.Mail;
import ch.puzzle.ln.pos.service.dto.InvoiceDTO;
import ch.puzzle.ln.pos.service.util.ConvertUtil;
import io.github.jhipster.config.JHipsterProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.springframework.mail.javamail.MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED;

/**
 * Service for sending emails.
 * <p>
 * We use the @Async annotation to send emails asynchronously.
 */
@Service
public class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);
    private static final String DATA = "data";

    private final JHipsterProperties jHipsterProperties;
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;
    private final ApplicationProperties applicationProperties;

    public MailService(JHipsterProperties jHipsterProperties, JavaMailSender javaMailSender,
                       SpringTemplateEngine templateEngine, ApplicationProperties applicationProperties) {
        this.jHipsterProperties = jHipsterProperties;
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.applicationProperties = applicationProperties;
    }

    @Async
    public void sendEmail(String subject, String content, boolean isHtml) {
        String sender = jHipsterProperties.getMail().getFrom();
        List<String> recipients = Arrays.stream(applicationProperties.getMail().getRecipient().split("([,;])"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        if (applicationProperties.getMail().isSend()) {
            LOG.info("Send email from '{}' [html '{}'] to '{}' with subject '{}' and content={}",
                sender, isHtml, StringUtils.join(recipients), subject, fixContentForLog(content));

            // Prepare message using a Spring helper
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            try {
                MimeMessageHelper message = new MimeMessageHelper(mimeMessage, MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
                message.setFrom(sender);
                message.setTo(recipients.toArray(new String[]{}));
                message.setSubject(subject);
                message.setText(content, isHtml);
                javaMailSender.send(mimeMessage);
                LOG.debug("Sent email to '{}'", recipients);
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.warn("Email could not be sent to '{}'", recipients, e);
                } else {
                    LOG.warn("Email could not be sent to '{}': {}", recipients, e.getMessage());
                }
            }
        } else {
            LOG.info("Not sending email from '{}' [html '{}'] to '{}' with subject '{}' and content={}",
                sender, isHtml, StringUtils.join(recipients), subject, content);
        }
    }

    @Async
    public void sendEmailFromTemplate(Map<String, Object> data, String templateName, String subject) {
        Context context = new Context(Locale.GERMAN);
        context.setVariable(DATA, data);
        String content = templateEngine.process(templateName, context);
        sendEmail(subject, content, true);
    }

    @Async
    public void sendOrderConfirmation(InvoiceDTO invoice) {
        Mail mail = applicationProperties.getMail();
        LOG.debug("Sending order confirmation email to '{}'", mail.getRecipient());
        String subject = mail.getSubject() + invoice.getReferenceIdShort();
        Double taxMultiplier = applicationProperties.getTaxMultiplier();
        String ticker = applicationProperties.getCurrencyTicker();

        Map<String, Object> data = new HashMap<>();

        List<List<String>> options = invoice.getOrderItems().stream()
            .flatMap(i -> i.getOptions()
                .stream()
                .map(o -> asList(
                    "left",
                    i.getItemType().name() + " " + o,
                    "right",
                    ConvertUtil.formatCurrency(ticker, i.getItemType().getPrice())
                ))
            ).collect(Collectors.toList());
        data.put("options", options);
        data.put("title", subject);
        data.put("invoice", invoice);
        data.put("taxBase", ConvertUtil.formatNumber(taxMultiplier * 100, 1));
        data.put("taxAmount", ConvertUtil.formatCurrency(ticker, invoice.getTotal() * taxMultiplier));
        data.put("total", ConvertUtil.formatCurrency(ticker, invoice.getTotal()));
        data.put("paymentText", mail.getPaymentText());

        sendEmailFromTemplate(data, "mail/orderConfirmation", subject);
    }

    private String fixContentForLog(String content) {
        return content.replaceAll("background-image: url\\([^)]+\\);", "");
    }
}
