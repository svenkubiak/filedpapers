<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
<div class="auth-container">
    <div class="auth-box">
        <span class="icon auth-logo">
            <i class="fas fa-bookmark fa-8x"></i>
        </span>
        <h1 class="auth-title">Reset Password</h1>
        <p class="auth-subtitle">Enter your email to reset your password.</p>

        <#if flash.success??>
            <div class="notification is-success is-light mb-5">
                <button class="delete"></button>
                ${flash.success}
            </div>
        </#if>
        <form action="/auth/forgot" method="POST">
            <div class="field">
                <div class="control has-icons-left<#if form.hasError("username")> has-icons-right</#if>">
                    <input class="input<#if form.hasError("username")> is-danger</#if>" type="email" placeholder="Your Email" name="username" id="username" value="" required>
                    <span class="icon is-small is-left">
                        <i class="fas fa-envelope"></i>
                    </span>
                    <#if form.hasError("username")>
                        <span class="icon is-small is-right">
                        <i class="fas fa-exclamation-triangle"></i>
                    </span>
                    </#if>
                </div>
                <#if form.hasError("username")>
                    <p class="help is-danger">${form.getError("username")}</p>
                </#if>
            </div>
            <div class="field">
                <div class="control">
                    <button type="submit" class="button is-link is-fullwidth<#if flash.forgot?? && flash.forgot == "success"> is-disabled</#if>"<#if flash.forgot?? && flash.forgot == "success"> disabled</#if>>
                        Reset Password
                    </button>
                </div>
            </div>
        </form>
        <div class="auth-links">
            <a href="/auth/login">Back to Login</a>
        </div>
    </div>
</div>
</@layout.myLayout>