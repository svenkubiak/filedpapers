<#import "../layout.ftl" as layout>
<@layout.myLayout "Layout">
<nav class="breadcrumb" aria-label="breadcrumbs">
    <ul>
        <li><a href="/dashboard"><span class="icon is-small"><i class="fas fa-user-cog"></i></span><span>Profile</span></a></li>
    </ul>
</nav>
<div class="columns is-multiline">
    <div class="column is-half">
        <div class="profile-form">
            <form action="/dashboard/profile/change-username" method="POST" class="profile-section" onsubmit="showLoading('update-email-button')">
                <h2 class="section-title">Change email</h2>
                <div class="form-field">
                    <label class="label">Current email</label>
                    <div class="control has-icons-left">
                        <input class="input" type="email" value="${username}" disabled>
                        <span class="icon is-small is-left">
                            <i class="fas fa-envelope"></i>
                        </span>
                    </div>
                </div>
                <div class="form-field">
                    <label class="label">New email</label>
                    <div class="control has-icons-left<#if form.hasError("username")> has-icons-right</#if>">
                        <input class="input<#if form.hasError("username")> is-danger</#if>" type="email" placeholder="new@email.com" name="username">
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
                <div class="form-field">
                    <label class="label">Password</label>
                    <div class="control has-icons-left">
                        <input class="input" type="password" placeholder="Confirm with your password" name="password">
                        <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
                    </div>
                </div>
                <div class="form-field">
                    <div class="control">
                        <button type="submit" class="button is-link is-fullwidth" id="update-email-button">
                            Update Email
                        </button>
                    </div>
                </div>
            </form>
            <form action="/dashboard/profile/change-password" method="post" class="profile-section" onsubmit="showLoading('update-password-button')">
                <h2 class="section-title">Change password</h2>
                <div class="form-field">
                    <label class="label">Current password</label>
                    <div class="control has-icons-left">
                        <input class="input" type="password" placeholder="Current password" name="password">
                        <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
                    </div>
                </div>
                <div class="form-field">
                    <label class="label">New password</label>
                    <div class="control has-icons-left<#if form.hasError("new-password")> has-icons-right</#if>">
                        <input class="input<#if form.hasError("new-password")> is-danger</#if>" type="password" placeholder="New password" name="new-password">
                        <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
                        <#if form.hasError("new-password")>
                            <span class="icon is-small is-right">
                            <i class="fas fa-exclamation-triangle"></i>
                        </span>
                        </#if>
                    </div>
                    <#if form.hasError("new-password")>
                        <p class="help is-danger">${form.getError("new-password")}</p>
                    </#if>
                </div>
                <div class="form-field">
                    <label class="label">Confirm new password</label>
                    <div class="control has-icons-left<#if form.hasError("confirm-password")> has-icons-right</#if>">
                        <input class="input<#if form.hasError("confirm-password")> is-danger</#if>" type="password" placeholder="Confirm new password" name="confirm-password">
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
                <div class="form-field">
                    <div class="control">
                        <button type="submit" class="button is-link is-fullwidth" id="update-password-button">
                            Update Password
                        </button>
                    </div>
                </div>
            </form>
            <form class="profile-section">
                <h2 class="section-title">Danger zone</h2>
                <div class="form-field">
                    <div class="control">
                        <button type="submit" class="button is-danger is-fullwidth" id="delete-account">
                            Delete account
                        </button>
                    </div>
                </div>
            </form>
        </div>
    </div>
</div>
</@layout.myLayout>