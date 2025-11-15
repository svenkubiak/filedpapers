<#import "../layout.ftl" as layout>
<@layout.myLayout "Layout">
<div class="level">
    <div class="level-left">
        <nav class="breadcrumb" aria-label="breadcrumbs">
            <ul>
                <li><a href="/dashboard"><span class="icon is-small"><i class="fas fa-user"></i></span><span>${i18n("profile.breadcrumbs")}</span></a></li>
            </ul>
        </nav>
    </div>
</div>
<div class="columns is-multiline">
    <div class="column is-half">
        <div class="profile-form">
            <#if mfa>
                <h2 class="section-title">${i18n("profile.mfa.title")}</h2>
                <div class="notification is-success is-light">
                    ${i18n("profile.mfa.enabled")}
                </div>
                <div class="form-field">
                    <div class="control">
                        <a href="/dashboard/profile?mfa=disable" class="button is-link is-fullwidth">
                            ${i18n("profile.mfa.disable")}
                        </a>
                    </div>
                </div>
                <#if mfaFallback??>
                    <div class="notification is-warning">
                        ${i18n("profile.mfa.fallback.1")}
                        <br><br>
                        🔒 ${i18n("profile.mfa.fallback.2")}: <code>${mfaFallback}</code>
                        <br><br>
                        ${i18n("profile.mfa.fallback.3")?no_esc}
                    </div>
                </#if>
            <#else>
               <#if enrollMfa>
                   <form action="/dashboard/profile/enable-mfa" method="POST" class="profile-section">
                       <h2 class="section-title">${i18n("profile.mfa.title")}</h2>
                       <div class="notification is-info is-light">
                           ${i18n("profile.mfa.enable.info")}
                       </div>
                       <#if qrCode??>
                           <img src="data:image/png;base64,${qrCode}"/>
                       </#if>
                       <div class="field">
                           <label class="label">${i18n("profile.totp.label")}</label>
                           <div class="control is-flex is-justify-content-center" style="gap: 0.5rem;">
                               <input class="input otp-input is-medium has-text-centered" name="otp-1" type="text" maxlength="1" pattern="\d*" inputmode="numeric" />
                               <input class="input otp-input is-medium has-text-centered" name="otp-2" type="text" maxlength="1" pattern="\d*" inputmode="numeric" />
                               <input class="input otp-input is-medium has-text-centered" name="otp-3" type="text" maxlength="1" pattern="\d*" inputmode="numeric" />
                               <input class="input otp-input is-medium has-text-centered" name="otp-4" type="text" maxlength="1" pattern="\d*" inputmode="numeric" />
                               <input class="input otp-input is-medium has-text-centered" name="otp-5" type="text" maxlength="1" pattern="\d*" inputmode="numeric" />
                               <input class="input otp-input is-medium has-text-centered" name="otp-6" type="text" maxlength="1" pattern="\d*" inputmode="numeric" />
                           </div>
                       </div>
                       <div class="form-field">
                           <div class="control">
                               <button type="submit" class="button is-link is-fullwidth">
                                   ${i18n("profile.mfa.validate")}
                               </button>
                           </div>
                       </div>
                       <@csrfform/>
                   </form>
               <#else>
                   <h2 class="section-title">${i18n("profile.mfa.title")}</h2>
                   <div class="notification is-info is-light">
                       ${i18n("profile.mfa.info")}
                   </div>
                   <div class="notification is-warning is-light">
                       ${i18n("profile.mfa.disabled")}
                   </div>
                   <div class="form-field">
                       <div class="control">
                           <a href="/dashboard/profile?mfa=enable" class="button is-link is-fullwidth">
                               ${i18n("profile.mfa.enable")}
                           </a>
                       </div>
                   </div>
                </#if>
            </#if>
            <form action="/dashboard/profile/logout-devices" method="POST" class="profile-section">
                <h2 class="section-title">${i18n("profile.logout.devices.title")}</h2>
                <div class="notification is-info is-light">
                    ${i18n("profile.logout.devices.info")}
                </div>
                <div class="form-field">
                    <div class="control">
                        <button type="submit" class="button is-link is-fullwidth" id="logout-devices">
                            ${i18n("profile.logout.devices.submit")}
                        </button>
                    </div>
                </div>
                <@csrfform/>
            </form>
            <form action="/dashboard/profile/language" method="POST" class="profile-section">
                <h2 class="section-title">${i18n("profile.language.title")}</h2>
                <div class="notification is-info is-light">
                    ${i18n("profile.language.info")}
                </div>
                <div class="form-field">
                    <div class="control has-icons-left">
                        <div class="select is-medium is-fullwidth">
                            <select name="language">
                                <#list languages as key, value>
                                    <option value="${key}"<#if language == key> selected</#if>>${value}</option>
                                </#list>
                            </select>
                        </div>
                        <span class="icon is-medium is-left">
                            <i class="fas fa-globe"></i>
                        </span>
                    </div>
                </div>
                <div class="form-field">
                    <div class="control">
                        <button type="submit" class="button is-link is-fullwidth">
                            ${i18n("profile.language.save")}
                        </button>
                    </div>
                </div>
                <@csrfform/>
            </form>
            <form action="/dashboard/profile/change-username" method="POST" class="profile-section" onsubmit="showLoading('update-email-button')">
                <h2 class="section-title">${i18n("profile.email.title")}</h2>
                <#if !confirmed>
                <div class="notification is-warning">
                    ${i18n("profile.email.notification")?no_esc}
                </div>
                </#if>
                <div class="form-field">
                    <label class="label">${i18n("profile.email.label")}</label>
                    <div class="control has-icons-left">
                        <input class="input" type="email" value="${username}" disabled>
                        <span class="icon is-small is-left">
                            <i class="fas fa-envelope"></i>
                        </span>
                    </div>
                </div>
                <div class="form-field">
                    <label class="label">${i18n("profile.email.new.label")}</label>
                    <div class="control has-icons-left<#if form.hasError("username")> has-icons-right</#if>">
                        <input class="input<#if form.hasError("username")> is-danger</#if>" type="email" placeholder="${i18n("profile.email.placeholder")}" name="username">
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
                    <label class="label">${i18n("profile.email.password.label")}</label>
                    <div class="control has-icons-left">
                        <input class="input" type="password" placeholder="${i18n("profile.email.password.placeholder")}" name="password">
                        <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
                    </div>
                </div>
                <div class="form-field">
                    <div class="control">
                        <button type="submit" class="button is-link is-fullwidth" id="update-email-button">
                            ${i18n("profile.email.button")}
                        </button>
                    </div>
                </div>
                <@csrfform/>
            </form>
            <form action="/dashboard/profile/change-password" method="post" class="profile-section" onsubmit="showLoading('update-password-button')">
                <h2 class="section-title">${i18n("profile.password.title")}</h2>
                <div class="form-field">
                    <label class="label">${i18n("profile.password.label")}</label>
                    <div class="control has-icons-left">
                        <input class="input" type="password" placeholder="${i18n("profile.password.placeholder")}" name="password">
                        <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
                    </div>
                </div>
                <div class="form-field">
                    <label class="label">${i18n("profile.password.new.label")}</label>
                    <div class="control has-icons-left<#if form.hasError("new-password")> has-icons-right</#if>">
                        <input class="input<#if form.hasError("new-password")> is-danger</#if>" type="password" placeholder="${i18n("profile.password.new.placeholder")}" name="new-password">
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
                    <label class="label">${i18n("profile.password.confirm.label")}</label>
                    <div class="control has-icons-left<#if form.hasError("confirm-password")> has-icons-right</#if>">
                        <input class="input<#if form.hasError("confirm-password")> is-danger</#if>" type="password" placeholder="${i18n("profile.password.confirm.placeholder")}" name="confirm-password">
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
                            ${i18n("profile.password.button")}
                        </button>
                    </div>
                </div>
                <@csrfform/>
            </form>
            <form class="profile-section">
                <h2 class="section-title">${i18n("profile.danger.title")}</h2>
                <div class="form-field">
                    <div class="control">
                        <a href="/dashboard/resync" class="button is-warning is-fullwidth">${i18n("profile.resync.button")}</a>
                    </div>
                </div>
                <div class="form-field">
                    <div class="control">
                        <button type="submit" class="button is-danger is-fullwidth" id="delete-account">
                            ${i18n("profile.danger.button")}
                        </button>
                    </div>
                </div>
            </form>
        </div>
    </div>
</div>
</@layout.myLayout>