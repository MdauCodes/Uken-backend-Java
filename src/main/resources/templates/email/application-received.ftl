<#import "macros.ftl" as m>
<@m.layout title="Application received">
  <p class="h1">We received your application, ${name}.</p>
  <p>Thank you for applying to join Ukena. Our team will review your application and get back to you within 5 working days.</p>
  <div class="detail-row"><span>Application ID</span><strong>${applicationId}</strong></div>
  <p class="muted">Keep this ID for your records. If you have questions, reply to this email quoting your application ID.</p>
  <p>We look forward to sharing your craft with the world.</p>
</@m.layout>