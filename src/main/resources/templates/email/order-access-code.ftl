<#import "macros.ftl" as m>
<@m.layout title="Your code to view order ${orderRef}">
  <h1 class="h1">Here's your code, ${name}.</h1>
  <p>Enter this code to see order <strong>${orderRef}</strong>. It works for <strong>10 minutes</strong>.</p>
  <div style="background:#f5f0eb;border-radius:8px;padding:24px;text-align:center;margin:24px 0;">
    <span style="font-size:40px;font-weight:700;letter-spacing:12px;color:#1a1a1a;">${code}</span>
  </div>
  <p class="muted">If you didn't ask to see this order, you can ignore this email. Nobody can see it without this code.</p>
</@m.layout>
