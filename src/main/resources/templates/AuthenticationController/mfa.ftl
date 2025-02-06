<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
<div class="auth-container">
    <div class="auth-box">
        <span class="icon auth-logo">
            <i class="fas fa-bookmark fa-8x"></i>
        </span>
        <h1 class="auth-title">Two-Step Verification</h1>
        <p class="auth-subtitle">Please enter your TOTP.</p>

        <#if flash.error??>
            <div class="notification is-danger is-light mb-5">
                <button class="delete"></button>
                ${flash.error}
            </div>
        </#if>
        <form action="/auth/mfa" method="POST" onsubmit="showLoading('login-button')">
            <div class="field">
                <div class="control has-icons-left<#if form.hasError("mfa")> has-icons-right</#if>">
                    <input class="input<#if form.hasError("mfa")> is-danger</#if>" type="text" placeholder="6 digits code" name="mfa" id="mfa" value="" required>
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
                    <button type="submit" class="button is-link is-fullwidth" id="login-button">Verify</button>
                </div>
            </div>
        </form>
        <div class="auth-links">
            <a href="/auth/login">Back to Login</a>
        </div>
    </div>
</div>
</@layout.myLayout>