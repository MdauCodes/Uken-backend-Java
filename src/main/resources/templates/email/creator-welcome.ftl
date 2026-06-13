<#import "macros.ftl" as m>
<@m.layout title="Welcome to Ukena">
  <p class="h1">Welcome to Ukena, ${name}.</p>
  <p>Your application has been approved. Your creator account is ready.</p>
  <div class="detail-row"><span>Your creator page</span><strong>${creatorId}</strong></div>
  <#if tempPassword?? && tempPassword?has_content>
    <div class="detail-row"><span>Temporary password</span><strong>${tempPassword}</strong></div>
    <p class="muted">Please change your password after your first login.</p>
  <#else>
    <p class="muted">Your account has been re-activated. Use your existing password to sign in. If you have forgotten it, use the "Forgot password" link on the sign-in page.</p>
  </#if>
  <a href="${loginUrl}" class="btn">Sign in to your account</a>
  <p class="muted">Head to your dashboard to complete your profile, add your story, and list your first product.</p>
  <p class="muted">If you did not expect this email, please contact us immediately.</p>
</@m.layout>