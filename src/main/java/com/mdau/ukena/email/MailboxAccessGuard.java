package com.mdau.ukena.email;

import com.mdau.ukena.security.CurrentUser;
import com.mdau.ukena.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Backs {@code @PreAuthorize("@mailboxAccessGuard.canAccess(#mailboxId, principal)")}
 * on mailbox-scoped endpoints. Admins bypass the {@link MailboxAccess} table entirely;
 * staff (ROLE_SUPPORT) need an explicit grant row.
 */
@Component("mailboxAccessGuard")
@RequiredArgsConstructor
public class MailboxAccessGuard {

    private final MailboxAccessRepository mailboxAccessRepository;

    public boolean canAccess(UUID mailboxId, CurrentUser user) {
        if (user == null || mailboxId == null) return false;
        if (user.role() == UserRole.ROLE_ADMIN) return true;
        return mailboxAccessRepository.existsByMailboxIdAndUserId(mailboxId, user.id());
    }
}
