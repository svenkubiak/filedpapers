<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
<div class="auth-container">
    <div class="auth-box">
        <span class="icon auth-logo">
            <i class="fa-solid fa-bookmark fa-8x"></i>
        </span>
        <h1 class="auth-title">${i18n("auth.login.title")}</h1>
        <p class="auth-subtitle">${i18n("auth.login.subtitle")}</p>
        <#if flash.success??>
            <div class="notification is-success is-light mb-5">
                <button class="delete"></button>
                ${flash.success}
            </div>
        </#if>
        <#if flash.error??>
            <div class="notification is-danger is-light mb-5">
                <button class="delete"></button>
                ${flash.error}
            </div>
        </#if>
        <form action="/auth/login" method="POST" onsubmit="showLoading('login-button')">
            <div class="field">
                <div class="control has-icons-left<#if form.hasError("username")> has-icons-right</#if>">
                    <input class="input<#if form.hasError("username")> is-danger</#if>" type="email" placeholder="${i18n("auth.login.username.placeholder")}" id="username" value="<#if form.username??>${form.username}</#if>" name="username" required>
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
                    <input class="input<#if form.hasError("password")> is-danger</#if>" type="password" placeholder="${i18n("auth.login.password.placeholder")}" name="password" required>
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
                <div class="control">
                    <label class="checkbox">
                        <input type="checkbox" value="1" name="rememberme">
                        ${i18n("auth.login.remember")}
                    </label>
                </div>
            </div>
            <div class="field">
                <div class="control">
                    <button type="submit" class="button is-link is-fullwidth" id="login-button">${i18n("auth.login.button")}</button>
                </div>
            </div>
        </form>
        <div class="auth-links">
            <#if registration>
            <a href="/auth/signup">${i18n("auth.login.link.signup")}</a>
            <span>·</span>
            </#if>
            <a href="/auth/forgot">${i18n("auth.login.link.forgot")}</a>
        </div>
    </div>
</div>
</@layout.myLayout>