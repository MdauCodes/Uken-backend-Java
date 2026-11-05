package com.mdau.ukena.application;

import com.mdau.ukena.application.dto.AccountApplicationRequest;
import com.mdau.ukena.auth.AuthService;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.creator.Creator;
import com.mdau.ukena.creator.CreatorRepository;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.user.User;
import com.mdau.ukena.user.UserRepository;
import com.mdau.ukena.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AccountApplicationTest {

    private ApplicationRepository applications;
    private UserRepository users;
    private CreatorRepository creators;
    private AuthService auth;
    private ApplicationService service;
    private final Map<String, Creator> savedCreators = new HashMap<>();

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        users = mock(UserRepository.class);
        creators = mock(CreatorRepository.class);
        auth = mock(AuthService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(applications.existsById(anyString())).thenReturn(false);
        when(creators.existsById(anyString())).thenAnswer(inv -> savedCreators.containsKey(inv.<String>getArgument(0)));
        when(creators.save(any(Creator.class))).thenAnswer(inv -> {
            Creator c = inv.getArgument(0);
            savedCreators.put(c.getId(), c);
            return c;
        });
        when(creators.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(savedCreators.get(inv.<String>getArgument(0))));
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        service = new ApplicationService(applications, users, creators, encoder, mock(EmailService.class), auth);
    }

    private AccountApplicationRequest individual(String email) {
        return new AccountApplicationRequest(email, "longenough1", "Wanjiru Kamau", "+254 714 220 381",
                "INDIVIDUAL", "Murang'a, Kenya", "Pottery", 12,
                "Hand-thrown bowls fired beneath the Kikuyu hills.", null, null, null, null, null);
    }

    @Test
    void createsMakerAccountWithHiddenProfileAndLinkedApplication() {
        service.submitWithAccount(individual("Wanjiru@Example.com"));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().getRole()).isEqualTo(UserRole.ROLE_CREATOR);
        assertThat(user.getValue().getEmail()).isEqualTo("wanjiru@example.com");
        assertThat(user.getValue().getPhone()).isEqualTo("+254 714 220 381");

        Creator profile = savedCreators.get(user.getValue().getCreatorId());
        assertThat(profile).isNotNull();
        assertThat(profile.getDeletedAt()).as("hidden from buyers until approved").isNotNull();

        ArgumentCaptor<ArtisanApplication> app = ArgumentCaptor.forClass(ArtisanApplication.class);
        verify(applications).save(app.capture());
        assertThat(app.getValue().getUserId()).isEqualTo(user.getValue().getId());
        assertThat(app.getValue().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        verify(auth).issueFor(user.getValue());
    }

    @Test
    void businessApplicantsAppearUnderTheirTradingName() {
        service.submitWithAccount(new AccountApplicationRequest("coop@example.com", "longenough1", "Mary Mutua",
                "+254 722 105 993", "BUSINESS", "Machakos, Kenya", "Pottery", 9,
                "A worker-owned pottery workshop of fourteen members.", "Machakos Clay Collective",
                "Cooperative", "P051234567X", 14, "Mary Mutua, chairperson"));

        Creator profile = savedCreators.values().iterator().next();
        assertThat(profile.getFullName()).isEqualTo("Machakos Clay Collective");
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().isBusiness()).isTrue();
        assertThat(user.getValue().getCompanyName()).isEqualTo("Machakos Clay Collective");
    }

    @Test
    void businessApplicantsMustNameTheBusiness() {
        assertThatThrownBy(() -> service.submitWithAccount(new AccountApplicationRequest("coop@example.com",
                "longenough1", "Mary Mutua", "+254 722 105 993", "BUSINESS", "Machakos, Kenya", "Pottery", 9,
                "A worker-owned pottery workshop of fourteen members.", " ", null, null, null, "Mary")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("trading name");
        verify(users, never()).save(any());
    }

    @Test
    void refusesAnEmailThatAlreadyHasAnAccount() {
        when(users.existsByEmailIgnoreCase("buyer@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.submitWithAccount(individual("buyer@example.com")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Sign in and apply from your account");
        verify(users, never()).save(any());
        verify(applications, never()).save(any());
    }
}
