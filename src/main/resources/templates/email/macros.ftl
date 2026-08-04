<#macro layout title>
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title}</title>
  <style>
    body { margin:0; padding:0; background:#FAF7F2; font-family: 'DM Sans', Arial, sans-serif; }
    .wrap { max-width:560px; margin:0 auto; background:#fff; }
    .header { background:#1a0f07; padding:28px 32px; text-align:center; }
    .body { padding:32px; color:#2C2420; line-height:1.7; font-size:15px; }
    .h1 { font-size:24px; font-weight:500; color:#1a0f07; margin:0 0 16px; }
    .btn { display:inline-block; background:#C1694F; color:#fff !important;
           text-decoration:none; padding:12px 28px; border-radius:4px;
           font-size:14px; font-weight:500; margin:20px 0; }
    .detail-row { display:flex; justify-content:space-between;
                  padding:8px 0; border-bottom:1px solid #f0ece6; font-size:14px; }
    .about { background:#f5f1eb; padding:20px 32px; font-size:13px;
             line-height:1.6; color:#6B5040; border-top:1px solid #ecdfd0; }
    .footer { background:#f5f1eb; padding:16px 32px 24px;
              font-size:12px; color:#8B6040; text-align:center; }
    .muted { color:#8B6040; font-size:13px; }
  </style>
</head>
<body>
<div class="wrap">
  <div class="header">
    <img src="${baseUrl}/email-assets/uken-logo-light.png" alt="UKEN — From Heart to Hands" height="56" style="height:56px; width:auto; display:inline-block;">
  </div>
  <div class="body">
    <#nested>
  </div>
  <div class="about">
    <strong style="color:#1a0f07;">About UKEN</strong> — we connect independent artisans and farmers
    across Kenya and the UK directly with buyers who value the story behind what they own. Every
    piece is made by hand, priced fairly, and shipped with the maker's name attached — no
    middleman markup, no anonymous factory batch.
  </div>
  <div class="footer">
    &copy; Ukena &nbsp;·&nbsp; Kenyan craft, world-class quality<br>
    <a href="${baseUrl}" style="color:#C1694F;">ukena.co.uk</a>
  </div>
</div>
</body>
</html>
</#macro>



