<#import "./layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "Two-factor authentication">
<h1 class="auth__title">${i18n("auth.mfa.title")}</h1>
<p class="auth__subtitle">${i18n("auth.mfa.subtitle")}</p>

<#if flash.error??>
    <div class="note note--rust" style="margin-top:22px">
        <@icons.icon "warn"/>
        <div><div class="note__text">${flash.error}</div></div>
    </div>
</#if>

<form class="auth__form" action="/auth/mfa" method="POST" data-busy="login-button">
    <div class="field">
        <label class="label" for="mfa">${i18n("auth.mfa.placeholder")}</label>
        <input class="input<#if form.hasError("mfa")> is-invalid</#if>" type="text" id="mfa" name="mfa"
               inputmode="numeric" autocomplete="one-time-code" required>
        <#if form.hasError("mfa")>
            <p class="help is-invalid">${form.getError("mfa")}</p>
        </#if>
    </div>

    <button type="submit" class="btn btn--ink btn--lg btn--block" id="login-button">${i18n("auth.mfa.button")}</button>
    <@csrfform/>
</form>

<div class="auth__links">
    <a href="/auth/logout">${i18n("auth.mfa.cancel")}</a>
</div>
</@layout.myLayout>
