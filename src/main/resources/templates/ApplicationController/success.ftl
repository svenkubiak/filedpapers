<#import "./layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "Success">
<div class="auth__brand">
    <span class="auth__mark" style="background:var(--moss);color:#fff"><@icons.icon "check"/></span>
    <span class="auth__wordmark">Filed Papers<span class="auth__dot">.</span></span>
</div>
<h1 class="auth__title">${i18n("application.success.title")}</h1>
<p class="auth__subtitle">${i18n("application.success.subtitle")}</p>
<div class="auth__links">
    <a href="/auth/login">${i18n("application.success.link")}</a>
</div>
</@layout.myLayout>
