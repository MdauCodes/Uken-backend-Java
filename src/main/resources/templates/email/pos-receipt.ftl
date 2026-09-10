<#import "macros.ftl" as m>
<@m.layout title="Your receipt">
  <p class="h1">Thank you, ${name}.</p>
  <p>Here's your receipt for today's purchase at the Uken stall — you've already got everything in hand, there's nothing more to do.</p>
  <div class="detail-row"><span>Receipt reference</span><strong>${orderRef}</strong></div>
  <#list items as item>
  <div class="detail-row"><span>${item.quantity}× ${item.name}</span><span>${item.lineTotal}</span></div>
  </#list>
  <div class="detail-row"><span><strong>Total paid</strong></span><strong>${totalFormatted}</strong></div>
  <p class="muted">Thank you for supporting East African makers directly — see you at the next market.</p>
</@m.layout>
