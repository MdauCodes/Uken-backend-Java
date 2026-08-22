<#import "macros.ftl" as m>
<@m.layout title="You've been added to Ukena">
  <p class="h1">Welcome to the Ukena team, ${name}.</p>
  <p>An admin has created a ${role} account for you.</p>
  <div class="detail-row"><span>Email</span><strong>${email}</strong></div>
  <div class="detail-row"><span>Temporary password</span><strong>${tempPassword}</strong></div>
  <p class="muted">Please sign in and change your password as soon as you can — or use the "Forgot password" link on the sign-in page to set your own.</p>
  <a href="${loginUrl}" class="btn">Sign in</a>
  <p class="muted">If you did not expect this email, please contact us immediately.</p>
</@m.layout>
