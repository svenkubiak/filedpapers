package services;

import constants.Const;
import constants.Required;
import io.mangoo.email.Mail;
import io.mangoo.exceptions.MangooTemplateEngineException;
import io.mangoo.i18n.Messages;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Singleton
public class NotificationService {
    private static final Logger LOG = LogManager.getLogger(NotificationService.class);
    private final Messages messages;
    private final DataService dataService;
    private final String from;
    private final String url;

    @Inject
    public NotificationService(DataService dataService,
                               Messages messages,
                               @Named("smtp.from") String from,
                               @Named("application.url") String url) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.messages = Objects.requireNonNull(messages, Required.MESSAGES);
        this.from = Objects.requireNonNull(from, Required.FROM);
        this.url = Objects.requireNonNull(url, Required.URL);
    }

    public void forgotPassword(String username, String token) {
        Objects.requireNonNull(username, Required.USERNAME);
        Objects.requireNonNull(token, Required.TOKEN);

        var user = dataService.findUser(username);
        if (user != null) {
            try {
                messages.reload(Locale.of(user.getLanguage()));
                Map<String, Object> content = new HashMap<>();
                content.put("token", token);
                content.put("url", url);
                content.put("messages", messages);

                Mail.newMail()
                        .from(from)
                        .subject(Const.EMAIL_PREFIX + " " + messages.get("email.forgot.password.subject"))
                        .to(user.getUsername())
                        .textMessage("emails/forgot_password.ftl", content)
                        .send();
            } catch (MangooTemplateEngineException e) {
                LOG.error("Failed to send forgot password email", e);
            }
        }
    }

    public void confirmEmail(String username, String token) {
        Objects.requireNonNull(username, Required.USERNAME);
        Objects.requireNonNull(token, Required.TOKEN);

        var user = dataService.findUser(username);
        if (user != null) {
            try {
                messages.reload(Locale.of(user.getLanguage()));
                Map<String, Object> content = new HashMap<>();
                content.put("token", token);
                content.put("url", url);
                content.put("messages", messages);

                Mail.newMail()
                        .from(from)
                        .subject(Const.EMAIL_PREFIX + " " + messages.get("email.confirm.email.subject"))
                        .to(user.getUsername())
                        .textMessage("emails/confirm_email.ftl", content)
                        .send();
            } catch (MangooTemplateEngineException e) {
                LOG.error("Failed to send confirm email", e);
            }
        }
    }

    public void accountChanged(String username, String message) {
        Objects.requireNonNull(username, Required.USERNAME);
        Objects.requireNonNull(message, Required.MESSAGE);

        var user = dataService.findUser(username);
        if (user != null) {
            try {
                messages.reload(Locale.of(user.getLanguage()));
                Map<String, Object> content = new HashMap<>();
                content.put("messages", messages);
                content.put("message", message);

                Mail.newMail()
                        .from(from)
                        .subject(Const.EMAIL_PREFIX + " " + messages.get("email.account.changes.subject"))
                        .to(user.getUsername())
                        .textMessage("emails/account_changed.ftl", content)
                        .send();
            } catch (MangooTemplateEngineException e) {
                LOG.error("Failed to send account changed information", e);
            }
        }
    }
}