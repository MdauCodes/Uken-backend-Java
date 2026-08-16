package com.mdau.ukena.pos;

import com.mdau.ukena.order.OrderService;
import com.mdau.ukena.order.dto.OrderDto;
import com.mdau.ukena.pos.dto.PosOrderRequest;
import com.mdau.ukena.pos.dto.PosPaymentIntentResponse;
import com.mdau.ukena.product.ProductService;
import com.mdau.ukena.product.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PosService {

    private final OrderService orderService;
    private final ProductService productService;
    private final StripeTerminalService stripeTerminalService;

    /** Default POS browse grid — Uken's own catalogue, including market-only pieces
     *  that are deliberately hidden from the public shop. */
    public List<ProductDto> browseProducts() {
        return productService.browseForPos();
    }

    public OrderDto createOrder(PosOrderRequest req) {
        return orderService.placePos(req.items(), req.customerEmail(), req.customerFullName());
    }

    /** Creates the PaymentIntent and immediately hands it to the configured reader.
     *  The order stays PENDING until the payment_intent.succeeded webhook lands
     *  (see PaymentService) — the POS page polls getOrder() to find out. */
    public PosPaymentIntentResponse charge(String displayId) {
        OrderDto order = orderService.getByDisplayId(displayId);
        PosPaymentIntentResponse intent = stripeTerminalService.createPaymentIntent(displayId, order.totalPence());
        stripeTerminalService.dispatchToReader(intent.paymentIntentId());
        return intent;
    }

    public OrderDto getOrder(String displayId) {
        return orderService.getByDisplayId(displayId);
    }
}
