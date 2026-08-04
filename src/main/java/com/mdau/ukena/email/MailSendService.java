package com.mdau.ukena.email;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.email.dto.SendEmailRequest;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailSendService {

    private final MailboxConnectionService connectionService;
    private final InboxService inboxService;
    private final BrevoMailboxSender brevoMailboxSender;

    public void send(Mailbox mailbox, SendEmailRequest req, List<MultipartFile> attachments) {
        sendInternal(mailbox, req, attachments, null, null);
    }

    /** {@code inReplyToMessageId} is the original message's RFC 822 Message-ID header. */
    public void reply(Mailbox mailbox, SendEmailRequest req, List<MultipartFile> attachments,
                       String inReplyToMessageId) {
        sendInternal(mailbox, req, attachments, inReplyToMessageId, inReplyToMessageId);
    }

    public void forward(Mailbox mailbox, SendEmailRequest req, List<MultipartFile> attachments) {
        sendInternal(mailbox, req, attachments, null, null);
    }

    public long saveDraft(Mailbox mailbox, SendEmailRequest req, List<MultipartFile> attachments) {
        return appendToFolder(mailbox, req, attachments, EmailFolder.DRAFTS, true);
    }

    public long updateDraft(Mailbox mailbox, long existingUid, SendEmailRequest req, List<MultipartFile> attachments) {
        deleteMessage(mailbox, EmailFolder.DRAFTS, existingUid);
        return saveDraft(mailbox, req, attachments);
    }

    public void deleteMessage(Mailbox mailbox, EmailFolder folder, long uid) {
        Store store = null;
        try {
            store = connectionService.openImapStore(mailbox);
            Folder f = inboxService.resolveFolder(store, folder);
            f.open(Folder.READ_WRITE);
            try {
                UIDFolder uidFolder = (UIDFolder) f;
                Message m = uidFolder.getMessageByUID(uid);
                if (m != null) {
                    m.setFlag(Flags.Flag.DELETED, true);
                    f.expunge();
                }
            } finally {
                f.close(false);
            }
        } catch (MessagingException e) {
            log.error("IMAP delete failed for mailbox {} uid {}: {}", mailbox.getAddress(), uid, e.getMessage());
            throw ApiException.internalError("Could not remove that message. Please try again.");
        } finally {
            closeQuietly(store);
        }
    }

    // ── send ─────────────────────────────────────────────────────────────

    /** Sends via Brevo's API when configured — direct SMTP to mail.privateemail.com
     *  is blocked outbound from Railway's IP ranges (confirmed: both port 465 and
     *  587 time out on connect, while IMAP on 993 works fine), so Brevo is the
     *  reliable path. Falls back to direct SMTP only if Brevo isn't configured. */
    private void sendInternal(Mailbox mailbox, SendEmailRequest req, List<MultipartFile> attachments,
                               String inReplyTo, String references) {
        Session session = connectionService.smtpSession(mailbox);
        try {
            MimeMessage message = buildMimeMessage(session, mailbox, req, attachments);
            if (inReplyTo != null && !inReplyTo.isBlank()) {
                message.setHeader("In-Reply-To", inReplyTo);
                message.setHeader("References", references != null ? references : inReplyTo);
            }

            if (brevoMailboxSender.isConfigured()) {
                brevoMailboxSender.send(mailbox, req, attachments, inReplyTo, references);
                log.info("Email sent via Brevo from {} to {}", mailbox.getAddress(), req.to());
            } else {
                sendViaDirectSmtp(session, mailbox, message);
                log.info("Email sent via direct SMTP from {} to {}", mailbox.getAddress(), req.to());
            }

            copyToSentFolder(mailbox, message);
        } catch (MessagingException | IOException e) {
            log.error("Send failed for mailbox {}: {}", mailbox.getAddress(), e.getMessage());
            throw ApiException.internalError("Failed to send email. Please try again.");
        }
    }

    private void sendViaDirectSmtp(Session session, Mailbox mailbox, MimeMessage message) throws MessagingException {
        Transport transport = null;
        try {
            transport = session.getTransport(connectionService.smtpProtocol(mailbox));
            transport.connect(mailbox.getSmtpHost(), mailbox.getSmtpPort(),
                    mailbox.getUsername(), mailbox.getPassword());
            transport.sendMessage(message, message.getAllRecipients());
        } finally {
            if (transport != null) {
                try {
                    if (transport.isConnected()) transport.close();
                } catch (MessagingException ignored) {
                }
            }
        }
    }

    /** Namecheap Private Email doesn't auto-copy SMTP-submitted mail into Sent —
     *  append it ourselves so it shows up in the Sent folder like a normal MUA. Best-effort. */
    private void copyToSentFolder(Mailbox mailbox, MimeMessage message) {
        Store store = null;
        try {
            store = connectionService.openImapStore(mailbox);
            Folder f = inboxService.resolveFolder(store, EmailFolder.SENT);
            f.open(Folder.READ_WRITE);
            try {
                f.appendMessages(new Message[]{message});
            } finally {
                f.close(false);
            }
        } catch (MessagingException e) {
            log.warn("Could not copy sent message to Sent folder for {}: {}", mailbox.getAddress(), e.getMessage());
        } finally {
            closeQuietly(store);
        }
    }

    // ── drafts ───────────────────────────────────────────────────────────

    private long appendToFolder(Mailbox mailbox, SendEmailRequest req, List<MultipartFile> attachments,
                                 EmailFolder folder, boolean asDraft) {
        Store store = null;
        try {
            Session session = connectionService.smtpSession(mailbox);
            MimeMessage message = buildMimeMessage(session, mailbox, req, attachments);
            if (asDraft) message.setFlag(Flags.Flag.DRAFT, true);

            store = connectionService.openImapStore(mailbox);
            Folder f = inboxService.resolveFolder(store, folder);
            f.open(Folder.READ_WRITE);
            try {
                f.appendMessages(new Message[]{message});
                UIDFolder uidFolder = (UIDFolder) f;
                int count = f.getMessageCount();
                Message[] appended = f.getMessages(count, count);
                return uidFolder.getUID(appended[0]);
            } finally {
                f.close(false);
            }
        } catch (MessagingException | IOException e) {
            log.error("Failed to save to {} for mailbox {}: {}", folder, mailbox.getAddress(), e.getMessage());
            throw ApiException.internalError("Failed to save draft. Please try again.");
        } finally {
            closeQuietly(store);
        }
    }

    // ── message construction ────────────────────────────────────────────

    private MimeMessage buildMimeMessage(Session session, Mailbox mailbox, SendEmailRequest req,
                                          List<MultipartFile> attachments) throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(session);
        String senderName = req.senderName() != null && !req.senderName().isBlank()
                ? req.senderName() : mailbox.getDisplayName();
        message.setFrom(new InternetAddress(mailbox.getAddress(), senderName, "UTF-8"));

        for (String to : req.to()) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        }
        if (req.cc() != null) {
            for (String cc : req.cc()) message.addRecipient(Message.RecipientType.CC, new InternetAddress(cc));
        }
        if (req.bcc() != null) {
            for (String bcc : req.bcc()) message.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc));
        }

        message.setSubject(req.subject(), "UTF-8");
        message.setSentDate(new Date());

        if (attachments == null || attachments.isEmpty()) {
            message.setContent(req.htmlBody(), "text/html; charset=UTF-8");
        } else {
            MimeMultipart multipart = new MimeMultipart("mixed");

            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(req.htmlBody(), "text/html; charset=UTF-8");
            multipart.addBodyPart(bodyPart);

            for (MultipartFile file : attachments) {
                if (file == null || file.isEmpty()) continue;
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.setDataHandler(new jakarta.activation.DataHandler(
                        new ByteArrayDataSource(file.getBytes(), file.getContentType())));
                attachmentPart.setFileName(MimeUtility.encodeText(
                        file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment"));
                multipart.addBodyPart(attachmentPart);
            }
            message.setContent(multipart);
        }

        message.saveChanges();
        return message;
    }

    private void closeQuietly(Store store) {
        if (store == null) return;
        try {
            if (store.isConnected()) store.close();
        } catch (MessagingException e) {
            log.debug("Error closing IMAP store: {}", e.getMessage());
        }
    }
}
