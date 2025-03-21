<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
<div class="auth-container">
    <div class="auth-box">
        <span class="icon auth-logo">
            <i class="fas fa-bookmark fa-8x"></i>
        </span>
        <h1 class="auth-title">${i18n("auth.signup.title")}</h1>
        <p class="auth-subtitle">${i18n("auth.signup.subtitle")}</p>
        <form action="/auth/signup" method="POST" onsubmit="showLoading('signup-button')">
            <div class="field">
                <div class="control has-icons-left<#if form.hasError("username")> has-icons-right</#if>">
                    <input class="input<#if form.hasError("username")> is-danger</#if>" type="email" placeholder="${i18n("auth.signup.username.placeholder")}" id="username" value="<#if form.username??>${form.username}</#if>"  name="username" required>
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
                <div class="control has-icons-left<#if form.hasError("password")> has-icons-right</#if>">
                    <input class="input<#if form.hasError("password")> is-danger</#if>" type="password" placeholder="${i18n("auth.signup.password.placeholder")}" name="password" required>
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
                    <input class="input<#if form.hasError("confirm-password")> is-danger</#if>" type="password" placeholder="${i18n("auth.signup.confirm.placeholder")}" name="confirm-password" required>
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
                        ${i18n("auth.signup.button")}
                    </button>
                </div>
            </div>
        </form>
        <div class="auth-links">
            <a href="/auth/login">${i18n("auth.signup.link")}</a>
        </div>
    </div>
</div>
</@layout.myLayout>