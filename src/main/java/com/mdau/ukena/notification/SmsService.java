package com.mdau.ukena.notification;

/**
 * SMS notification interface.
 * Active implementation: TwilioSmsService (disabled until credentials set).
 * Fallback: all notifications go via EmailService.
 * Enable by setting TWILIO_ENABLED=true in Railway variables.
 */
public interface SmsService {

    /**
     * Send an SMS. Only fires for UK numbers (+44).
     * Returns true if sent, false if skipped or failed.
     */
    boolean sendSms(String toPhone, String message);

    /** Returns true if SMS is enabled and configured. */
    boolean isEnabled();
}