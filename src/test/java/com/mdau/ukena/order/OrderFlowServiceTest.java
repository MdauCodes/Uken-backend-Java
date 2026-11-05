package com.mdau.ukena.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.notification.EmailService;
import com.mdau.ukena.order.dto.AdvanceOrderRequest;
import com.mdau.ukena.order.dto.ReportProblemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static com.mdau.ukena.order.FulfilmentStage.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderFlowServiceTest {

    private OrderRepository orders;
    private OrderEventRepository events;
    private EmailService email;
    private OrderFlowService flow;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        events = mock(OrderEventRepository.class);
        email = mock(EmailService.class);
        OrderService orderService = mock(OrderService.class);
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        flow = new OrderFlowService(orders, events, orderService, email, new ObjectMapper());
    }

    private Order order(FulfilmentStage stage, String fulfilment) {
        Order o = Order.builder().id(UUID.randomUUID()).displayId("UKN-1").buyerEmail("sophie@example.com")
                .buyerFullName("Sophie").status(stage.legacyStatus() != null ? stage.legacyStatus() : OrderStatus.SHIPPED)
                .channel(OrderChannel.ONLINE).fulfilment(fulfilment).stage(stage).build();
        when(orders.findByDisplayId("UKN-1")).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void eachStageHasOneNextStepPerDeliveryMethod() {
        assertThat(OrderFlowService.nextStage("HUB", PAID)).isEqualTo(IN_PRODUCTION);
        assertThat(OrderFlowService.nextStage("HUB", IN_PRODUCTION)).isEqualTo(AT_HUB);
        assertThat(OrderFlowService.nextStage("HUB", AT_HUB)).isEqualTo(PACKED);
        assertThat(OrderFlowService.nextStage("HUB", PACKED)).as("moves with its flight").isNull();
        assertThat(OrderFlowService.nextStage("HUB", IN_AIR)).as("moves with its flight").isNull();
        assertThat(OrderFlowService.nextStage("HUB", AT_UK_BASE)).isEqualTo(OUT_FOR_DELIVERY);
        assertThat(OrderFlowService.nextStage("PICKUP", AT_UK_BASE)).isEqualTo(READY_TO_COLLECT);
        assertThat(OrderFlowService.nextStage("HUB", DELIVERED)).isEqualTo(COMPLETED);
        assertThat(OrderFlowService.nextStage("PICKUP", READY_TO_COLLECT)).isEqualTo(COMPLETED);
    }

    @Test
    void refusesToSkipAStage() {
        order(PAID, "HUB");
        assertThatThrownBy(() -> flow.advance("UKN-1", new AdvanceOrderRequest("PACKED", 900, null, null, null), null))
                .isInstanceOf(ApiException.class).hasMessageContaining("next step is IN_PRODUCTION");
    }

    @Test
    void packingNeedsAWeightAndRecordsIt() {
        Order o = order(AT_HUB, "HUB");
        assertThatThrownBy(() -> flow.advance("UKN-1", new AdvanceOrderRequest("PACKED", null, null, null, null), null))
                .isInstanceOf(ApiException.class).hasMessageContaining("Weigh");

        flow.advance("UKN-1", new AdvanceOrderRequest("PACKED", 1400, null, null, null), null);
        assertThat(o.getStage()).isEqualTo(PACKED);
        assertThat(o.getPackedWeightGrams()).isEqualTo(1400);
        assertThat(o.getStatus()).as("legacy status kept in step").isEqualTo(OrderStatus.PREPARING);
        verify(events).save(argThat(e -> e.getStage() == PACKED && e.getNote().contains("1.4 kg")));
    }

    @Test
    void handingToRoyalMailNeedsTrackingAndEmailsTheBuyer() {
        Order o = order(AT_UK_BASE, "HUB");
        assertThatThrownBy(() -> flow.advance("UKN-1", new AdvanceOrderRequest("OUT_FOR_DELIVERY", null, "Royal Mail Tracked 48", null, null), null))
                .isInstanceOf(ApiException.class);

        flow.advance("UKN-1", new AdvanceOrderRequest("OUT_FOR_DELIVERY", null, "Royal Mail Tracked 48", "TT 1234 5678 9GB", null), null);
        assertThat(o.getTrackingNumber()).isEqualTo("TT 1234 5678 9GB");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(email).sendOrderStatusUpdate("sophie@example.com", "Sophie", "UKN-1", "OUT_FOR_DELIVERY");
    }

    @Test
    void buyerConfirmingCompletesTheOrder() {
        Order o = order(DELIVERED, "HUB");
        flow.confirmReceived(o, "Sophie");
        assertThat(o.getStage()).isEqualTo(COMPLETED);
        assertThat(o.getBuyerConfirmedAt()).isNotNull();
    }

    @Test
    void problemsCanOnlyBeReportedOnceOnTheWayButCancellationCanBeAskedEarly() {
        Order early = order(IN_PRODUCTION, "HUB");
        assertThatThrownBy(() -> flow.reportProblem(early, "Sophie", new ReportProblemRequest("Arrived damaged", null)))
                .isInstanceOf(ApiException.class);

        flow.reportProblem(early, "Sophie", new ReportProblemRequest("Cancellation request", "Changed my mind"));
        assertThat(early.getStage()).isEqualTo(ISSUE);
        assertThat(early.getStatus()).as("legacy status left alone on a problem").isEqualTo(OrderStatus.PREPARING);
        assertThat(early.getIssueKind()).isEqualTo("Cancellation request");
    }
}
