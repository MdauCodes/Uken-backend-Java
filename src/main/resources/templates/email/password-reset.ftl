<#import "macros.ftl" as m>
<@m.layout title="Reset your Ukena password">
  <h1 class="h1">Reset your password, ${name}.</h1>
  <p>Use the OTP below to reset your Ukena password. It expires in <strong>10 minutes</strong>.</p>
  <div style="background:#f5f0eb;border-radius:8px;padding:24px;text-align:center;margin:24px 0;">
    <span style="font-size:40px;font-weight:700;letter-spacing:12px;color:#1a1a1a;">${otp}</span>
  </div>
  <p class="muted">If you did not request a password reset, you can safely ignore this email — your password will not change.</p>
</@m.layout>