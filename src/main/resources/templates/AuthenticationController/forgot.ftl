<#import "./layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "Forgot password">
<h1 class="auth__title">${i18n("auth.forgot.title")}</h1>
<p class="auth__subtitle">${i18n("auth.forgot.subtitle")}</p>

<#if flash.success??>
    <div class="note note--moss" style="margin-top:22px">
        <@icons.icon "check-circle"/>
        <div><div class="note__text">${flash.success}</div></div>
    </div>
</#if>

<form class="auth__form" action="/auth/forgot" method="POST">
    <div class="field">
        <label class="label" for="username">${i18n("auth.forgot.placeholder")}</label>
        <input class="input<#if form.hasError("username")> is-invalid</#if>" type="email" id="username"
               name="username" autocomplete="username" required>
        <#if form.hasError("username")>
            <p class="help is-invalid">${form.getError("username")}</p>
        </#if>
    </div>

    <button type="submit" class="btn btn--ink btn--lg btn--block"
            <#if flash.forgot?? && flash.forgot == "success">disabled</#if>>
        ${i18n("auth.forgot.submit")}
    </button>
    <@csrfform/>
</form>

<div class="auth__links">
    <a href="/auth/login">${i18n("auth.forgot.link")}</a>
</div>
</@layout.myLayout>
