<#import "macros.ftl" as m>
<@m.layout title="Your welcome discount — Ukena">
  <p class="h1">Welcome to Ukena.</p>
  <p>Here's ${percentOff}% off your first order — traceable coffee, tea, textiles and craft, straight from the maker to your door.</p>
  <div class="detail-row"><span>Your code</span><strong>${promoCode}</strong></div>
  <p class="muted">Enter it at checkout. One use per order.</p>
  <a href="${shopUrl}" class="btn">Start shopping</a>
</@m.layout>
