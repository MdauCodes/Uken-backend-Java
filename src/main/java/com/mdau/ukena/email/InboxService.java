package com.mdau.ukena.email;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.email.dto.EmailAttachmentDto;
import com.mdau.ukena.email.dto.EmailMessageDetailDto;
import com.mdau.ukena.email.dto.EmailMessageDto;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Live IMAP fetch, short-lived cache (see {@code CacheConfig} — Caffeine,
 * dynamic cache creation, default 5-minute TTL is overridden per-call-site
 * needs by keeping folder listings cheap to re-fetch). No message-mirroring
 * DB schema — right-sized for a 3-mailbox internal tool.
 */
@Slf4j
@Service
public class InboxService {

    private final MailboxConnectionService connectionService;

    public InboxService(MailboxConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Cacheable(value = "emailFolder", key = "#mailbox.id + ':' + #folder + ':' + #page")
    public List<EmailMessageDto> listMessages(Mailbox mailbox, EmailFolder folder, int page, int pageSize) {
        Store store = null;
        try {
            store = connectionService.openImapStore(mailbox);
            Folder f = resolveFolder(store, folder);
            f.open(Folder.READ_ONLY);
            try {
                int total = f.getMessageCount();
                if (total == 0) return List.of();

                int end = total - page * pageSize;
                if (end < 1) return List.of();
                int start = Math.max(1, end - pageSize + 1);

                Message[] messages = f.getMessages(start, end);

                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                fp.add(FetchProfile.Item.FLAGS);
                fp.add(FetchProfile.Item.CONTENT_INFO);
                fp.add(UIDFolder.FetchProfileItem.UID);
                f.fetch(messages, fp);

                UIDFolder uidFolder = (UIDFolder) f;
                List<EmailMessageDto> result = new ArrayList<>(messages.length);
                for (Message m : messages) {
                    result.add(toListDto(uidFolder, m));
                }
                Collections.reverse(result); // newest first
                return result;
            } finally {
                f.close(false);
            }
        } catch (MessagingException e) {
            log.error("IMAP list failed for mailbox {} folder {}: {}", mailbox.getAddress(), folder, e.getMessage());
            throw ApiException.internalError("Could not reach the mail server. Please try again.");
        } finally {
            closeQuietly(store);
        }
    }

    @Cacheable(value = "emailMessage", key = "#mailbox.id + ':' + #folder + ':' + #uid")
    public EmailMessageDetailDto getMessage(Mailbox mailbox, EmailFolder folder, long uid) {
        Store store = null;
        try {
            store = connectionService.openImapStore(mailbox);
            Folder f = resolveFolder(store, folder);
            f.open(Folder.READ_ONLY);
            try {
                UIDFolder uidFolder = (UIDFolder) f;
                Message m = uidFolder.getMessageByUID(uid);
                if (m == null) throw ApiException.notFound("Message not found");
                return toDetailDto(uid, m);
            } finally {
                f.close(false);
            }
        } catch (MessagingException e) {
            log.error("IMAP fetch failed for mailbox {} uid {}: {}", mailbox.getAddress(), uid, e.getMessage());
            throw ApiException.internalError("Could not load this message. Please try again.");
        } finally {
            closeQuietly(store);
        }
    }

    public byte[] getAttachmentBytes(Mailbox mailbox, EmailFolder folder, long uid, String partId) {
        Store store = null;
        try {
            store = connectionService.openImapStore(mailbox);
            Folder f = resolveFolder(store, folder);
            f.open(Folder.READ_ONLY);
            try {
                UIDFolder uidFolder = (UIDFolder) f;
                Message m = uidFolder.getMessageByUID(uid);
                if (m == null) throw ApiException.notFound("Message not found");
                Part part = findPart(m, partId);
                if (part == null) throw ApiException.notFound("Attachment not found");
                try (InputStream in = part.getInputStream();
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    in.transferTo(out);
                    return out.toByteArray();
                }
            } finally {
                f.close(false);
            }
        } catch (MessagingException | IOException e) {
            log.error("IMAP attachment fetch failed for mailbox {} uid {} part {}: {}",
                    mailbox.getAddress(), uid, partId, e.getMessage());
            throw ApiException.internalError("Could not download this attachment. Please try again.");
        } finally {
            closeQuietly(store);
        }
    }

    // ── folder resolution ────────────────────────────────────────────────

    Folder resolveFolder(Store store, EmailFolder folder) throws MessagingException {
        for (String name : candidateNames(folder)) {
            Folder f = store.getFolder(name);
            if (f.exists()) return f;
        }
        throw ApiException.internalError("Could not locate the " + folder + " folder on the mail server");
    }

    private String[] candidateNames(EmailFolder folder) {
        return switch (folder) {
            case INBOX -> new String[]{"INBOX"};
            case SENT -> new String[]{"Sent", "Sent Items", "INBOX.Sent"};
            case DRAFTS -> new String[]{"Drafts", "INBOX.Drafts"};
        };
    }

    // ── message → DTO mapping ───────────────────────────────────────────

    private EmailMessageDto toListDto(UIDFolder uidFolder, Message m) throws MessagingException {
        InternetAddress from = firstAddress(m.getFrom());
        return new EmailMessageDto(
                uidFolder.getUID(m),
                nullToEmpty(m.getSubject()),
                from != null ? from.getAddress() : "",
                from != null && from.getPersonal() != null ? from.getPersonal() : "",
                toInstant(m),
                snippet(m),
                isSeen(m),
                hasAttachments(m));
    }

    private EmailMessageDetailDto toDetailDto(long uid, Message m) throws MessagingException {
        InternetAddress from = firstAddress(m.getFrom());
        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        List<EmailAttachmentDto> attachments = new ArrayList<>();
        try {
            walkParts(m, "", text, html, attachments);
        } catch (IOException e) {
            throw new MessagingException("Failed to parse message body", e);
        }

        String messageId = m instanceof MimeMessage mm ? mm.getMessageID() : null;

        return new EmailMessageDetailDto(
                uid,
                nullToEmpty(m.getSubject()),
                from != null ? from.getAddress() : "",
                from != null && from.getPersonal() != null ? from.getPersonal() : "",
                addressStrings(m.getRecipients(Message.RecipientType.TO)),
                addressStrings(m.getRecipients(Message.RecipientType.CC)),
                toInstant(m),
                html.length() > 0 ? html.toString() : null,
                text.length() > 0 ? text.toString() : null,
                isSeen(m),
                attachments,
                messageId);
    }

    // ── MIME body walking ───────────────────────────────────────────────

    /** Populates text/html bodies and collects attachment metadata, recursing into nested multiparts.
     *  {@code partId} is a dot-path of part indices ("2", "2.1", ...) resolvable by {@link #findPart}. */
    private void walkParts(Part part, String partId, StringBuilder text, StringBuilder html,
                            List<EmailAttachmentDto> attachments) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                String childId = partId.isEmpty() ? String.valueOf(i + 1) : partId + "." + (i + 1);
                walkParts(bp, childId, text, html, attachments);
            }
            return;
        }

        String disposition = part.getDisposition();
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (part.getFileName() != null && !part.isMimeType("text/*"));

        if (attachment) {
            String filename = part.getFileName();
            if (filename != null) {
                try {
                    filename = MimeUtility.decodeText(filename);
                } catch (Exception ignored) {
                    // keep raw filename if it isn't RFC2047-encoded
                }
            }
            attachments.add(new EmailAttachmentDto(
                    partId, filename != null ? filename : "attachment",
                    part.getContentType(), part.getSize() > 0 ? part.getSize() : 0));
            return;
        }

        if (part.isMimeType("text/html")) {
            html.append(part.getContent());
        } else if (part.isMimeType("text/plain")) {
            text.append(part.getContent());
        }
    }

    private Part findPart(Part root, String partId) throws MessagingException, IOException {
        String[] indices = partId.split("\\.");
        Part current = root;
        for (String idxStr : indices) {
            int idx = Integer.parseInt(idxStr) - 1;
            if (!current.isMimeType("multipart/*")) return null;
            Multipart mp = (Multipart) current.getContent();
            if (idx < 0 || idx >= mp.getCount()) return null;
            current = mp.getBodyPart(idx);
        }
        return current;
    }

    // ── small helpers ───────────────────────────────────────────────────

    private InternetAddress firstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return null;
        return addresses[0] instanceof InternetAddress ia ? ia : null;
    }

    private List<String> addressStrings(Address[] addresses) {
        if (addresses == null) return List.of();
        List<String> result = new ArrayList<>();
        for (Address a : addresses) {
            if (a instanceof InternetAddress ia) result.add(ia.getAddress());
        }
        return result;
    }

    private Instant toInstant(Message m) throws MessagingException {
        var date = m.getSentDate() != null ? m.getSentDate() : m.getReceivedDate();
        return date != null ? date.toInstant() : Instant.EPOCH;
    }

    private boolean isSeen(Message m) throws MessagingException {
        return m.getFlags().contains(Flags.Flag.SEEN);
    }

    private boolean hasAttachments(Message m) throws MessagingException {
        try {
            return m.getContentType() != null && m.getContentType().toLowerCase().contains("multipart/mixed");
        } catch (Exception e) {
            return false;
        }
    }

    private String snippet(Message m) {
        try {
            Object content = m.getContent();
            String raw;
            if (content instanceof String s) {
                raw = s;
            } else if (content instanceof Multipart mp) {
                raw = firstTextSnippet(mp);
            } else {
                raw = "";
            }
            String stripped = raw.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            return stripped.length() > 160 ? stripped.substring(0, 160) + "…" : stripped;
        } catch (Exception e) {
            return "";
        }
    }

    private String firstTextSnippet(Multipart mp) {
        try {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain") || bp.isMimeType("text/html")) {
                    Object c = bp.getContent();
                    if (c instanceof String s) return s;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
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
