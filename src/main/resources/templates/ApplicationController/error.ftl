<#import "./layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "Error">
<div class="auth__brand">
    <span class="auth__mark" style="background:var(--rust);color:#fff"><@icons.icon "warn"/></span>
    <span class="auth__wordmark">Filed Papers<span class="auth__dot">.</span></span>
</div>
<h1 class="auth__title">${i18n("application.error.title")}</h1>
<p class="auth__subtitle">${i18n("application.error.subtitle")}</p>
<div class="auth__links">
    <a href="/auth/login">${i18n("application.error.link")}</a>
</div>
</@layout.myLayout>
