<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
<div class="auth-container">
    <div class="auth-box">
        <span class="icon auth-logo">
            <i class="fas fa-bookmark fa-8x"></i>
        </span>
        <h1 class="auth-title">${i18n("auth.mfa.title")}</h1>
        <p class="auth-subtitle">${i18n("auth.mfa.subtitle")}</p>
        <#if flash.error??>
            <div class="notification is-danger is-light mb-5">
                ${flash.error}
            </div>
        </#if>
        <form action="/auth/mfa" method="POST" onsubmit="showLoading('login-button')">
            <div class="field">
                <div class="control has-icons-left<#if form.hasError("mfa")> has-icons-right</#if>">
                    <input class="input<#if form.hasError("mfa")> is-danger</#if>" type="text" placeholder="${i18n("auth.mfa.placeholder")}" name="mfa" id="mfa" value="" required>
                    <span class="icon is-small is-left">
                        <i class="fas fa-lock"></i>
                    </span>
                    <#if form.hasError("mfa")>
                        <span class="icon is-small is-right">
                        <i class="fas fa-exclamation-triangle"></i>
                    </span>
                    </#if>
                </div>
                <#if form.hasError("mfa")>
                    <p class="help is-danger">${form.getError("mfa")}</p>
                </#if>
            </div>
            <div class="field">
                <div class="control">
                    <button type="submit" class="button is-link is-fullwidth" id="login-button">${i18n("auth.mfa.button")}</button>
                </div>
            </div>
            <@csrfform/>
        </form>
        <div class="auth-links">
            <a href="/auth/logout">${i18n("auth.mfa.cancel")}</a>
        </div>
    </div>
</div>
</@layout.myLayout>