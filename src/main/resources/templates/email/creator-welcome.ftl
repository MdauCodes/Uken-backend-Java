<#import "macros.ftl" as m>
<@m.layout title="Welcome to Ukena">
  <p class="h1">Welcome to Ukena, ${name}.</p>
  <p>Your application has been approved. Your creator account is ready.</p>
  <div class="detail-row"><span>Your creator page</span><strong>${creatorId}</strong></div>
  <div class="detail-row"><span>Temporary password</span><strong>${tempPassword}</strong></div>
  <a href="${loginUrl}" class="btn">Sign in to your account</a>
  <p class="muted">Please change your password after your first login. Head to your dashboard to complete your profile, add your story, and list your first product.</p>
  <p class="muted">If you did not expect this email, please contact us immediately.</p>
</@m.layout>