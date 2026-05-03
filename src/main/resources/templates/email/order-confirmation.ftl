<#import "macros.ftl" as m>
<@m.layout title="Order confirmed">
  <p class="h1">Thank you, ${name}.</p>
  <p>Your order <strong>${orderRef}</strong> is confirmed and being prepared with care.</p>
  <div class="detail-row"><span>Order reference</span><strong>${orderRef}</strong></div>
  <div class="detail-row"><span>Total paid</span><strong>${totalFormatted}</strong></div>
  <div class="detail-row"><span>Made by</span><span>${creatorNames}</span></div>
  <p class="muted">Your piece will be carefully packaged and shipped within 5–7 working days.</p>
  <a href="${ordersUrl}" class="btn">View your order</a>
  <p class="muted">Your purchase directly supports the artisans who made it — thank you.</p>
</@m.layout>