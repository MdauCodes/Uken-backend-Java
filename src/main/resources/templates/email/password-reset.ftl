<#import "macros.ftl" as m>
<@m.base subject="Reset your Ukena password">
  <h1 style="font-size:24px;margin:0 0 16px;">Reset your password, ${name}.</h1>
  <p style="margin:0 0 24px;">Use the OTP below to reset your Ukena password. It expires in <strong>10 minutes</strong>.</p>
  <div style="background:#f5f0eb;border-radius:8px;padding:24px;text-align:center;margin:0 0 24px;">
    <span style="font-size:40px;font-weight:700;letter-spacing:12px;color:#1a1a1a;">${otp}</span>
  </div>
  <p style="margin:0;color:#666;font-size:14px;">If you did not request a password reset, you can safely ignore this email — your password will not change.</p>
</@m.base>