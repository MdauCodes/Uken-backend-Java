<#import "macros.ftl" as m>
<@m.layout title="Complete your order">
  <p class="h1">You left something behind, ${name}.</p>
  <p>Your order <strong>${orderRef}</strong> is reserved but payment has not been completed yet.</p>
  <div class="detail-row"><span>Order reference</span><strong>${orderRef}</strong></div>
  <div class="detail-row"><span>Total</span><strong>${totalFormatted}</strong></div>
  <p class="muted">Your order will not be confirmed until payment is complete. The link below will take you directly to checkout.</p>
  <a href="${paymentLink}" class="btn">Complete payment</a>
  <p class="muted">If you did not place this order, you can safely ignore this email.</p>
</@m.layout>