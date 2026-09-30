<#import "./layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "Sign up">
<h1 class="auth__title">${i18n("auth.signup.title")}</h1>
<p class="auth__subtitle">${i18n("auth.signup.subtitle")}</p>

<form class="auth__form" action="/auth/signup" method="POST" data-busy="signup-button">
    <div class="field">
        <label class="label" for="username">${i18n("auth.signup.username.placeholder")}</label>
        <input class="input<#if form.hasError("username")> is-invalid</#if>" type="email" id="username"
               name="username" value="<#if form.username??>${form.username}</#if>" autocomplete="username" required>
        <#if form.hasError("username")>
            <p class="help is-invalid">${form.getError("username")}</p>
        </#if>
    </div>

    <div class="field">
        <label class="label" for="password">${i18n("auth.signup.password.placeholder")}</label>
        <input class="input<#if form.hasError("password")> is-invalid</#if>" type="password" id="password"
               name="password" autocomplete="new-password" required>
        <#if form.hasError("password")>
            <p class="help is-invalid">${form.getError("password")}</p>
        </#if>
    </div>

    <div class="field">
        <label class="label" for="confirm-password">${i18n("auth.signup.confirm.placeholder")}</label>
        <input class="input<#if form.hasError("confirm-password")> is-invalid</#if>" type="password"
               id="confirm-password" name="confirm-password" autocomplete="new-password" required>
        <#if form.hasError("confirm-password")>
            <p class="help is-invalid">${form.getError("confirm-password")}</p>
        </#if>
    </div>

    <button type="submit" class="btn btn--ink btn--lg btn--block" id="signup-button">${i18n("auth.signup.button")}</button>
    <@csrfform/>
</form>

<div class="auth__links">
    <a href="/auth/login">${i18n("auth.signup.link")}</a>
</div>
</@layout.myLayout>
