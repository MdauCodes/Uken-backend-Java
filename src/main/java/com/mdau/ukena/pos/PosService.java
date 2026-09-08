package com.mdau.ukena.pos;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.order.Order;
import com.mdau.ukena.order.OrderRepository;
import com.mdau.ukena.order.OrderService;
import com.mdau.ukena.order.OrderStatus;
import com.mdau.ukena.order.dto.OrderDto;
import com.mdau.ukena.pos.dto.PosOrderRequest;
import com.mdau.ukena.pos.dto.PosPaymentIntentResponse;
import com.mdau.ukena.pos.dto.PosReaderStatus;
import com.mdau.ukena.product.ProductService;
import com.mdau.ukena.product.dto.ProductDto;
import com.mdau.ukena.product.dto.ProductSummaryDto;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PosService {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final StripeTerminalService stripeTerminalService;

    /** Default POS browse grid — Uken's own catalogue, including market-only pieces
     *  that are deliberately hidden from the public shop. */
    public List<ProductDto> browseProducts() {
        return productService.browseForPos();
    }

    /** POS search — reaches beyond Uken's own catalogue, demo/preview listings
     *  excluded (see ProductRepository.searchForPos). */
    public List<ProductSummaryDto> searchProducts(String q, int size) {
        return productService.searchForPos(q, size);
    }

    public OrderDto createOrder(PosOrderRequest req) {
        return orderService.placePos(req.items(), req.customerEmail(), req.customerFullName());
    }

    /**
     * Creates (or safely reuses) the PaymentIntent and dispatches it to the reader.
     * Idempotent by design — Stripe's own guidance for a server-driven integration is
     * explicit that recreating a PaymentIntent on retry risks a double charge if the
     * first attempt actually went through; re-dispatching the SAME intent is required
     * instead. The order stays PENDING until the payment_intent.succeeded webhook
     * lands (see PaymentService) — the POS page polls getOrder() to find out.
     *
     * Deliberately NOT @Transactional. dispatchToReader() throws on the single most
     * common failure (reader offline/busy) — with a surrounding transaction, that
     * exception would roll back the order.setPaymentIntentId() save a few lines
     * above, silently undoing the "persist before dispatch" guarantee exactly when
     * it matters (a webhook or the next retry would find paymentIntentId still
     * null). Each repository call below is transactional on its own by default
     * with no enclosing transaction, so the persist genuinely commits before
     * dispatch is attempted, regardless of how dispatch turns out.
     */
    public PosPaymentIntentResponse charge(String displayId) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + displayId));
        if (order.getStatus() != OrderStatus.PENDING)
            throw ApiException.badRequest("This order is not awaiting payment (status: " + order.getStatus() + ")");

        String paymentIntentId = order.getPaymentIntentId();
        String clientSecret = null;

        if (paymentIntentId != null && !paymentIntentId.isBlank()) {
            PaymentIntent existing = stripeTerminalService.retrievePaymentIntent(paymentIntentId);
            switch (existing.getStatus()) {
                case "succeeded" -> {
                    // A webhook may simply not have landed yet — don't create a second
                    // charge for money that's already moved. Let the poll/webhook settle it.
                    return new PosPaymentIntentResponse(existing.getId(), existing.getClientSecret());
                }
                case "requires_payment_method" -> clientSecret = existing.getClientSecret(); // safe to re-dispatch as-is
                default -> {
                    // canceled, or any other non-retryable state — start clean.
                    stripeTerminalService.cancelPaymentIntent(paymentIntentId);
                    paymentIntentId = null;
                }
            }
        }

        if (paymentIntentId == null) {
            PosPaymentIntentResponse created = stripeTerminalService.createPaymentIntent(
                    displayId, order.getTotalPence(), receiptEmailFor(order));
            paymentIntentId = created.paymentIntentId();
            clientSecret = created.clientSecret();
        }

        // Persist BEFORE dispatch — a webhook that beats this HTTP response back must
        // still be able to resolve to this order (see PaymentService.resolveOrderForTerminalEvent).
        order.setPaymentIntentId(paymentIntentId);
        order.setLastPaymentError(null);
        orderRepository.save(order);

        stripeTerminalService.dispatchToReader(paymentIntentId);
        return new PosPaymentIntentResponse(paymentIntentId, clientSecret);
    }

    /** The operator's escape hatch for a stuck reader prompt (customer walked away,
     *  mis-rung sale) — cancels both the reader-side action and the PaymentIntent so
     *  the next charge attempt starts clean instead of failing with terminal_reader_busy.
     *  No-ops the local cleanup if the order already succeeded (a slow webhook could
     *  race with an operator tapping cancel) — never null out paymentIntentId on a
     *  completed order, since that's a real reference a later refund still needs. */
    @Transactional
    public void cancelCharge(String displayId) {
        Order order = orderRepository.findByDisplayId(displayId)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + displayId));
        stripeTerminalService.cancelReaderAction();
        if (order.getStatus() == OrderStatus.PENDING && order.getPaymentIntentId() != null) {
            stripeTerminalService.cancelPaymentIntent(order.getPaymentIntentId());
            order.setPaymentIntentId(null);
            orderRepository.save(order);
        }
    }

    /** The customer email collected (optionally) in the POS cart, if a real one was
     *  given — used as the PaymentIntent's receiptEmail so Stripe sends its own
     *  compliant card-present receipt. Never the walk-in sentinel. */
    private String receiptEmailFor(Order order) {
        String email = order.getBuyerEmail();
        return OrderService.WALK_IN_EMAIL.equalsIgnoreCase(email) ? null : email;
    }

    public PosReaderStatus readerStatus() {
        return stripeTerminalService.readerStatus();
    }

    public OrderDto getOrder(String displayId) {
        return orderService.getByDisplayId(displayId);
    }
}
