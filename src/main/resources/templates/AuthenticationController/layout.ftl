<#macro myLayout title="Filed Papers">
<#import "../_icons.ftl" as icons>
<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Filed Papers</title>
    <link rel="icon" type="image/x-icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png">
    <script>
        (function () {
            var stored = null;
            try { stored = localStorage.getItem('theme'); } catch (e) {}
            var dark = stored ? stored === 'dark'
                : window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
            document.documentElement.dataset.theme = dark ? 'dark' : 'light';
        })();
    </script>
    <link rel="stylesheet" href="/assets/css/app.css?v=${assetVersion!"0"}">
    <link rel="stylesheet" href="/assets/css/auth.css?v=${assetVersion!"0"}">
</head>
<body>
<@icons.sprite/>
<div class="auth">
    <div class="auth__card">
        <div class="auth__brand">
            <span class="auth__mark"><@icons.icon "bookmark"/></span>
            <span class="auth__wordmark">Filed Papers<span class="auth__dot">.</span></span>
        </div>
        <#nested/>
    </div>
</div>
<script src="/assets/js/auth.js?v=${assetVersion!"0"}"></script>
</body>
</html>
</#macro>
