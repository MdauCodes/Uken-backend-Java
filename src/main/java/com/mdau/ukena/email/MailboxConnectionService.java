package com.mdau.ukena.email;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Builds per-mailbox {@link Session}s from decrypted {@link Mailbox} credentials.
 *
 * Deliberately separate from the app-wide {@code JavaMailSender} bean (wired to
 * Brevo for transactional email in {@code EmailServiceImpl}) — these sessions talk
 * to mail.privateemail.com on behalf of a specific staff mailbox, an unrelated
 * concern.
 */
@Slf4j
@Service
public class MailboxConnectionService {

    public Store openImapStore(Mailbox mailbox) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.imaps.host", mailbox.getImapHost());
        props.put("mail.imaps.port", String.valueOf(mailbox.getImapPort()));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "15000");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(mailbox.getImapHost(), mailbox.getImapPort(),
                mailbox.getUsername(), mailbox.getPassword());
        return store;
    }

    public Transport openSmtpTransport(Mailbox mailbox) throws MessagingException {
        Session session = smtpSession(mailbox);
        Transport transport = session.getTransport(smtpProtocol(mailbox));
        transport.connect(mailbox.getSmtpHost(), mailbox.getSmtpPort(),
                mailbox.getUsername(), mailbox.getPassword());
        return transport;
    }

    public Session smtpSession(Mailbox mailbox) {
        String protocol = smtpProtocol(mailbox);
        Properties props = new Properties();
        props.put("mail." + protocol + ".host", mailbox.getSmtpHost());
        props.put("mail." + protocol + ".port", String.valueOf(mailbox.getSmtpPort()));
        props.put("mail." + protocol + ".auth", "true");
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "15000");
        if (protocol.equals("smtps")) {
            props.put("mail.smtps.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        return Session.getInstance(props);
    }

    /** Namecheap Private Email: port 465 is implicit-TLS (smtps); port 587 (and anything
     *  else) expects STARTTLS negotiated over a plain smtp connection. Forcing smtps on
     *  a STARTTLS-only port fails the handshake before any useful error surfaces. */
    public String smtpProtocol(Mailbox mailbox) {
        return mailbox.getSmtpPort() == 465 ? "smtps" : "smtp";
    }
}
