package com.mdau.ukena.notification;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Twilio SMS implementation (SDK 10.x).
 * Only sends to UK numbers (+44).
 * Gated by ukena.twilio.enabled=true (default: false).
 * Credentials: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER
 */
@Slf4j
@Service
public class TwilioSmsService implements SmsService {

    @Value("${ukena.twilio.account-sid:}")
    private String accountSid;

    @Value("${ukena.twilio.auth-token:}")
    private String authToken;

    @Value("${ukena.twilio.from-number:}")
    private String fromNumber;

    @Value("${ukena.twilio.enabled:false}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (enabled && isConfigured()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio SMS initialised (from={})", fromNumber);
        } else {
            log.info("Twilio SMS disabled - all notifications via email");
        }
    }

    @Override
    public boolean sendSms(String toPhone, String message) {
        if (!enabled || !isConfigured()) {
            log.debug("SMS skipped (disabled): to={}", toPhone);
            return false;
        }
        if (toPhone == null || !toPhone.startsWith("+44")) {
            log.debug("SMS skipped (non-UK number): to={}", toPhone);
            return false;
        }
        try {
            Message.creator(
                    new PhoneNumber(toPhone),
                    new PhoneNumber(fromNumber),
                    message
            ).create();
            log.info("SMS sent to {}", toPhone);
            return true;
        } catch (Exception e) {
            log.error("Twilio SMS failed to {}: {}", toPhone, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled && isConfigured();
    }

    private boolean isConfigured() {
        return accountSid != null && !accountSid.isBlank()
            && authToken  != null && !authToken.isBlank()
            && fromNumber != null && !fromNumber.isBlank();
    }
}