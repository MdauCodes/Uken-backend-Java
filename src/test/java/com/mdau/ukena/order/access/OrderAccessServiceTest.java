package com.mdau.ukena.order.access;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderAccessServiceTest {

    private OrderAccessCodeRepository codes;
    private EmailService email;
    private OrderAccessService service;
    private final List<OrderAccessCode> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        codes = mock(OrderAccessCodeRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        email = mock(EmailService.class);
        Order order = Order.builder().id(UUID.randomUUID()).displayId("UKN-9").buyerEmail("ella@example.com").buyerFullName("Ella").build();
        when(orders.findByDisplayId("UKN-9")).thenReturn(Optional.of(order));
        when(codes.save(any(OrderAccessCode.class))).thenAnswer(inv -> {
            OrderAccessCode c = inv.getArgument(0);
            if (!saved.contains(c)) saved.add(c);
            return c;
        });
        when(codes.findFirstByDisplayIdAndEmailIgnoreCaseOrderByCreatedAtDesc(anyString(), anyString()))
                .thenAnswer(inv -> saved.isEmpty() ? Optional.empty() : Optional.of(saved.get(saved.size() - 1)));
        when(codes.findByToken(anyString())).thenAnswer(inv -> saved.stream()
                .filter(c -> inv.getArgument(0).equals(c.getToken())).findFirst());
        service = new OrderAccessService(codes, orders, new BCryptPasswordEncoder(4), email);
    }

    private String requestCode() {
        service.sendCode(new TrackCodeRequest("UKN-9", "Ella@Example.com"));
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(email).sendOrderAccessCode(eq("ella@example.com"), eq("Ella"), eq("UKN-9"), code.capture());
        return code.getValue();
    }

    @Test
    void wrongEmailSendsNothingButAnswersTheSame() {
        service.sendCode(new TrackCodeRequest("UKN-9", "someone@else.com"));
        verifyNoInteractions(email);
        assertThat(saved).isEmpty();
    }

    @Test
    void theRightCodeUnlocksTheOrderOnceAndOnlyThatOrder() {
        String code = requestCode();
        assertThat(saved.get(0).getCodeHash()).as("code stored hashed").isNotEqualTo(code);

        String token = service.verify(new TrackVerifyRequest("UKN-9", "ella@example.com", code));
        assertThat(service.resolve("UKN-9", null, token).getDisplayId()).isEqualTo("UKN-9");

        assertThatThrownBy(() -> service.verify(new TrackVerifyRequest("UKN-9", "ella@example.com", code)))
                .as("a code works once").isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.resolve("UKN-9", null, "not-a-token")).isInstanceOf(ApiException.class);
    }

    @Test
    void lockedAfterFiveWrongTries() {
        String code = requestCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verify(new TrackVerifyRequest("UKN-9", "ella@example.com", wrong)))
                    .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> service.verify(new TrackVerifyRequest("UKN-9", "ella@example.com", code)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Too many tries");
    }
}
