<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
<div class="auth-container">
    <div class="auth-box">
    <span class="icon auth-logo" style="color: green;">
        <i class="fas fa-bookmark fa-8x"></i>
    </span>
        <h1 class="auth-title">${i18n("application.success.title")}</h1>
        <p class="auth-subtitle">${i18n("application.success.subtitle")}</p>
        <div class="auth-links">
            <a href="/auth/login">${i18n("application.success.link")}</a>
        </div>
    </div>
</div>
</@layout.myLayout>