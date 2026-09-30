<#import "./layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "Login">
<h1 class="auth__title">${i18n("auth.login.title")}</h1>
<p class="auth__subtitle">${i18n("auth.login.subtitle")}</p>

<#if flash.success??>
    <div class="note note--moss" style="margin-top:22px">
        <@icons.icon "check-circle"/>
        <div><div class="note__text">${flash.success}</div></div>
    </div>
</#if>
<#if flash.error??>
    <div class="note note--rust" style="margin-top:22px">
        <@icons.icon "warn"/>
        <div><div class="note__text">${flash.error}</div></div>
    </div>
</#if>

<form class="auth__form" action="/auth/login" method="POST" data-busy="login-button">
    <div class="field">
        <label class="label" for="username">${i18n("auth.login.username.placeholder")}</label>
        <input class="input<#if form.hasError("username")> is-invalid</#if>" type="email" id="username"
               name="username" value="<#if form.username??>${form.username}</#if>" autocomplete="username" required>
        <#if form.hasError("username")>
            <p class="help is-invalid">${form.getError("username")}</p>
        </#if>
    </div>

    <div class="field">
        <label class="label" for="password">${i18n("auth.login.password.placeholder")}</label>
        <input class="input<#if form.hasError("password")> is-invalid</#if>" type="password" id="password"
               name="password" autocomplete="current-password" required>
        <#if form.hasError("password")>
            <p class="help is-invalid">${form.getError("password")}</p>
        </#if>
    </div>

    <div class="auth__row">
        <label class="auth__check">
            <input type="checkbox" value="1" name="rememberme">
            <span class="checkbox" aria-hidden="true"><@icons.icon "check"/></span>
            <span>${i18n("auth.login.remember")}</span>
        </label>
    </div>

    <button type="submit" class="btn btn--ink btn--lg btn--block" id="login-button">${i18n("auth.login.button")}</button>
    <@csrfform/>
</form>

<div class="auth__links">
    <#if registration>
        <a href="/auth/signup">${i18n("auth.login.link.signup")}</a>
        <span>·</span>
    </#if>
    <a href="/auth/forgot">${i18n("auth.login.link.forgot")}</a>
</div>
</@layout.myLayout>
