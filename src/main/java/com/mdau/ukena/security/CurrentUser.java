package com.mdau.ukena.security;

import com.mdau.ukena.user.UserRole;
import java.util.UUID;

public record CurrentUser(
        UUID id,
        String email,
        UserRole role,
        String creatorId) {}