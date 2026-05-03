<#import "macros.ftl" as m>
<@m.layout title="Reset your password">
  <p class="h1">Reset your password, ${name}.</p>
  <p>We received a request to reset the password for your Ukena account.</p>
  <a href="${resetLink}" class="btn">Reset my password</a>
  <p class="muted">This link expires in 1 hour. If you did not request a password reset, you can safely ignore this email — your password will not change.</p>
</@m.layout>