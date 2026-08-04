package com.mdau.ukena.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.email.dto.SendEmailRequest;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Sends staff-mailbox email (compose/reply/forward) through Brevo's transactional
 * API instead of direct SMTP to mail.privateemail.com.
 *
 * Namecheap Private Email blocks SMTP submission (ports 465 and 587 alike) from
 * cloud/datacenter IP ranges, including Railway's — confirmed by a 10s connection
 * timeout on both ports, while IMAP (993) works fine, since Namecheap filters
 * submission traffic much more aggressively than fetch traffic. That's a network
 * block, not a credentials or protocol bug: no amount of retrying or TLS-mode
 * fixing on our side changes it. Brevo's own infrastructure isn't subject to that
 * block and is already configured and working for transactional email in this
 * app, so mailbox sends are routed through it with the mailbox's own address as
 * the visible sender. Reading mail and appending to the Sent folder still go
 * over IMAP (993) via {@link MailboxConnectionService}, which was never affected.
 *
 * Requires the sending domain (ukena.co.uk) to be verified in Brevo (SPF/DKIM) —
 * see the admin-facing setup notes. Without that, Brevo may reject the send or
 * the message may land in spam.
 */
@Component
public class BrevoMailboxSender {

    private final ObjectMapper objectMapper;
    private OkHttpClient httpClient;

    @Value("${ukena.email.brevo-api-key:}")
    private String brevoApiKey;

    @Value("${ukena.email.brevo-api-url}")
    private String brevoApiUrl;

    public BrevoMailboxSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public boolean isConfigured() {
        return brevoApiKey != null && !brevoApiKey.isBlank();
    }

    /** @param inReplyTo original Message-ID (RFC 822) when this is a reply, else null. */
    public void send(Mailbox mailbox, SendEmailRequest req, List<MultipartFile> attachments,
                      String inReplyTo, String references) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("name", mailbox.getDisplayName(), "email", mailbox.getAddress()));
        payload.put("to", toAddressList(req.to()));
        if (req.cc() != null && !req.cc().isEmpty()) payload.put("cc", toAddressList(req.cc()));
        if (req.bcc() != null && !req.bcc().isEmpty()) payload.put("bcc", toAddressList(req.bcc()));
        payload.put("subject", req.subject());
        payload.put("htmlContent", req.htmlBody());
        payload.put("replyTo", Map.of("email", mailbox.getAddress()));

        if (inReplyTo != null && !inReplyTo.isBlank()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("In-Reply-To", inReplyTo);
            headers.put("References", references != null && !references.isBlank() ? references : inReplyTo);
            payload.put("headers", headers);
        }

        List<Map<String, String>> encodedAttachments = encodeAttachments(attachments);
        if (!encodedAttachments.isEmpty()) payload.put("attachment", encodedAttachments);

        String json = objectMapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(brevoApiUrl)
                .addHeader("api-key", brevoApiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "no body";
                throw new IOException("Brevo error " + response.code() + ": " + err);
            }
        }
    }

    private List<Map<String, String>> toAddressList(List<String> addresses) {
        return addresses.stream().map(email -> Map.of("email", email)).toList();
    }

    private List<Map<String, String>> encodeAttachments(List<MultipartFile> attachments) throws IOException {
        if (attachments == null || attachments.isEmpty()) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (MultipartFile file : attachments) {
            if (file == null || file.isEmpty()) continue;
            result.add(Map.of(
                    "name", file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment",
                    "content", Base64.getEncoder().encodeToString(file.getBytes())));
        }
        return result;
    }
}
