<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
<div class="auth-container">
    <div class="auth-box">
    <span class="icon auth-logo">
        <i class="fas fa-bookmark fa-8x"></i>
    </span>
        <h1 class="auth-title">Reset Password</h1>
        <p class="auth-subtitle">Please enter your new password.</p>
        <form action="/auth/reset-password/${token}" method="POST" onsubmit="showLoading('signup-button')">
            <div class="field">
                <div class="control has-icons-left<#if form.hasError("password")> has-icons-right</#if>">
                    <input class="input<#if form.hasError("password")> is-danger</#if>" type="password" placeholder="Choose Password" name="password" required>
                    <span class="icon is-small is-left">
                    <i class="fas fa-lock"></i>
                </span>
                    <#if form.hasError("password")>
                        <span class="icon is-small is-right">
                    <i class="fas fa-exclamation-triangle"></i>
                </span>
                    </#if>
                </div>
                <#if form.hasError("password")>
                    <p class="help is-danger">${form.getError("password")}</p>
                </#if>
            </div>
            <div class="field">
                <div class="control has-icons-left<#if form.hasError("confirm-password")> has-icons-right</#if>">
                    <input class="input<#if form.hasError("confirm-password")> is-danger</#if>" type="password" placeholder="Confirm Password" name="confirm-password" required>
                    <span class="icon is-small is-left">
                    <i class="fas fa-lock"></i>
                </span>
                    <#if form.hasError("confirm-password")>
                        <span class="icon is-small is-right">
                    <i class="fas fa-exclamation-triangle"></i>
                </span>
                    </#if>
                </div>
                <#if form.hasError("confirm-password")>
                    <p class="help is-danger">${form.getError("confirm-password")}</p>
                </#if>
            </div>
            <div class="field">
                <div class="control">
                    <button type="submit" class="button is-link is-fullwidth" id="signup-button">
                        Reset Password
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>
</@layout.myLayout>