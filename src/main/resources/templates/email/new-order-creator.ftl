<#import "macros.ftl" as m>
<@m.layout title="New order received">
  <p class="h1">You have a new order, ${name}.</p>
  <p>A buyer has purchased one of your pieces. Please begin preparation.</p>
  <div class="detail-row"><span>Order reference</span><strong>${orderRef}</strong></div>
  <div class="detail-row"><span>Product</span><span>${productName}</span></div>
  <div class="detail-row"><span>Quantity</span><span>${quantity}</span></div>
  <a href="${ordersUrl}" class="btn">View order in dashboard</a>
  <p class="muted">Mark the order as Preparing once you have started, then Shipped when dispatched.</p>
</@m.layout>