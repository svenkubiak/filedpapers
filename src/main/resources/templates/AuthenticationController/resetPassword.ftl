<#import "./layout.ftl" as layout>
<@layout.myLayout "Reset password">
<h1 class="auth__title">${i18n("auth.reset.title")}</h1>
<p class="auth__subtitle">${i18n("auth.reset.subtitle")}</p>

<form class="auth__form" action="/auth/reset-password/${token}" method="POST" data-busy="reset-button">
    <div class="field">
        <label class="label" for="password">${i18n("auth.reset.password.placeholder")}</label>
        <input class="input<#if form.hasError("password")> is-invalid</#if>" type="password" id="password"
               name="password" autocomplete="new-password" required>
        <#if form.hasError("password")>
            <p class="help is-invalid">${form.getError("password")}</p>
        </#if>
    </div>

    <div class="field">
        <label class="label" for="confirm-password">${i18n("auth.reset.confirm.placeholder")}</label>
        <input class="input<#if form.hasError("confirm-password")> is-invalid</#if>" type="password"
               id="confirm-password" name="confirm-password" autocomplete="new-password" required>
        <#if form.hasError("confirm-password")>
            <p class="help is-invalid">${form.getError("confirm-password")}</p>
        </#if>
    </div>

    <button type="submit" class="btn btn--ink btn--lg btn--block" id="reset-button">${i18n("auth.reset.button")}</button>
    <@csrfform/>
</form>
</@layout.myLayout>
