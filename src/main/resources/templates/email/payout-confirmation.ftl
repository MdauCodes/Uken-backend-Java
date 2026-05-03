<#import "macros.ftl" as m>
<@m.layout title="Payout sent">
  <p class="h1">Your payout is on its way, ${name}.</p>
  <p>We have processed your earnings and sent a transfer to your registered account.</p>
  <div class="detail-row"><span>Amount</span><strong>${amountFormatted}</strong></div>
  <div class="detail-row"><span>Currency</span><span>${currency}</span></div>
  <p class="muted">Transfers typically arrive within 1–3 business days depending on your bank or mobile money provider.</p>
  <a href="${dashboardUrl}" class="btn">View your earnings</a>
  <p class="muted">Thank you for being part of Ukena.</p>
</@m.layout>