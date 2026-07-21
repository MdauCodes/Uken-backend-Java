<#import "macros.ftl" as m>
<@m.layout title="Add package weight — Ukena">
  <p class="h1">One quick thing, ${name}.</p>
  <p>We now calculate shipping by weight, so buyers see an accurate cost at checkout and you're paid correctly for it. We just need the packed weight of each unit for ${productCount} of your pieces:</p>
  <ul>
    <#list productNames as p>
      <li>${p}</li>
    </#list>
  </ul>
  <p class="muted">Estimate as accurately as you can — the weight of the piece plus its packaging, as it would leave your workshop.</p>
  <a href="${dashboardUrl}" class="btn">Add weights in your dashboard</a>
  <p class="muted">Until you add a weight, we'll use a standard estimate for these pieces so orders keep flowing — but your own number will be more accurate for both you and the buyer.</p>
</@m.layout>
