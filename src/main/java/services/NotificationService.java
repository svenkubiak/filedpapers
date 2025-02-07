package services;

import constants.Required;
import io.mangoo.email.Mail;
import io.mangoo.exceptions.MangooTemplateEngineException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NotificationService {
    private static final Logger LOG = LogManager.getLogger(NotificationService.class);
    private final DataService dataService;
    private final String from;
    private final String url;

    @Inject
    public NotificationService(DataService dataService,
                               @Named("smtp.from") String from,
                               @Named("application.url") String url) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.from = Objects.requireNonNull(from, Required.FROM);
        this.url = Objects.requireNonNull(url, Required.URL);
    }

    public void forgotPassword(String username, String token) {
        Objects.requireNonNull(username, Required.USERNAME);
        Objects.requireNonNull(token, Required.TOKEN);

        var user = dataService.findUser(username);
        if (user != null) {
            try {
                Map<String, Object> content = new HashMap<>();
                content.put("token", token);
                content.put("url", url);

                Mail.newMail()
                        .from(from)
                        .subject("[Filed Papers] Forgot Password")
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
                Map<String, Object> content = new HashMap<>();
                content.put("token", token);
                content.put("url", url);

                Mail.newMail()
                        .from(from)
                        .subject("[Filed Papers] Confirm Email")
                        .to(user.getUsername())
                        .textMessage("emails/confirm_email.ftl", content)
                        .send();
            } catch (MangooTemplateEngineException e) {
                LOG.error("Failed to send confirm email", e);
            }
        }
    }

    public void passwordChanged(String username) {
        Objects.requireNonNull(username, Required.USERNAME);

        var user = dataService.findUser(username);
        if (user != null) {
            try {
                Mail.newMail()
                        .from(from)
                        .subject("[Filed Papers] Confirm Email")
                        .to(user.getUsername())
                        .textMessage("emails/password_changed.ftl", new HashMap<>())
                        .send();
            } catch (MangooTemplateEngineException e) {
                LOG.error("Failed to send password change confirmation", e);
            }
        }
    }
}