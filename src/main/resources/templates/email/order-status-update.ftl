<#include "macros.ftl">
<@layout title="Your order ${orderRef} has been updated">
    <p class="h1">Order update</p>
    <p>Hi ${name},</p>
    <p>Your order <strong>${orderRef}</strong> has been updated.</p>
    <div style="background:#f9f6f2;border-radius:8px;padding:20px;margin:20px 0;">
        <p class="muted" style="margin:0 0 4px;text-transform:uppercase;letter-spacing:0.1em;font-size:12px;">Status</p>
        <p style="font-size:20px;color:#1a0f07;margin:0;font-weight:500;">${statusLabel}</p>
    </div>
    <a href="${trackUrl}" class="btn">Track your order</a>
    <p class="muted" style="margin-top:24px;">
        Questions? Reply to this email or visit <a href="${baseUrl}" style="color:#C1694F;">ukena.co.uk</a>
    </p>
</@layout>