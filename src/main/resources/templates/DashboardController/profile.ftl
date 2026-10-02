<#import "../layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "Profile">
<div class="column">
    <div class="page-head" style="margin-bottom:26px">
        <div>
            <h1 class="page-head__title">${i18n("profile.breadcrumbs")}</h1>
            <div class="page-head__meta">
                <span>${username}</span>
                <#if confirmed>
                    <span class="dot">·</span><span class="tag tag--moss">${i18n("profile.email.confirmed")}</span>
                <#else>
                    <span class="dot">·</span><span class="tag tag--gold">${i18n("profile.email.unconfirmed")}</span>
                </#if>
            </div>
        </div>
    </div>

    <#assign hasAvatar = avatar?? && avatar?has_content>
    <div class="sheet">
        <div class="sheet__title">${i18n("profile.avatar.title")}</div>
        <div class="sheet__body">
            <div class="line" style="border-bottom:none;flex-wrap:wrap">
                <div class="avatar-line">
                    <@layout.avatarImage "avatar--lg"/>
                    <div>
                        <div class="line__label">${i18n("profile.avatar.title")}</div>
                        <div class="line__hint">${i18n("profile.avatar.info")}</div>
                    </div>
                </div>
                <div class="line__ctrl actions">
                    <form action="/dashboard/profile/avatar" method="POST" enctype="multipart/form-data">
                        <button class="btn btn--ink" id="avatar-pick" type="button">
                            <@icons.icon "upload"/><#if hasAvatar>${i18n("profile.avatar.change")}<#else>${i18n("profile.avatar.upload")}</#if>
                        </button>
                        <input class="is-hidden" type="file" name="avatar" id="avatar-file"
                               accept="image/png,image/jpeg" data-max-bytes="${maxAvatarBytes?c}">
                        <@csrfform/>
                    </form>
                    <#if hasAvatar>
                        <form action="/dashboard/profile/avatar/delete" method="POST">
                            <button class="btn btn--danger btn--line" type="submit">
                                <@icons.icon "trash"/>${i18n("profile.avatar.remove")}
                            </button>
                            <@csrfform/>
                        </form>
                    </#if>
                </div>
            </div>
        </div>
    </div>

    <div class="sheet">
        <div class="sheet__title">${i18n("profile.mfa.title")}</div>
        <#if mfa>
            <p class="sheet__lead">${i18n("profile.mfa.info")}</p>
            <div class="sheet__body">
                <#if mfaFallback??>
                    <div class="note note--gold" style="margin:14px 0">
                        <@icons.icon "key"/>
                        <div>
                            <div class="note__title">${i18n("profile.mfa.fallback.1")}</div>
                            <div class="note__text">${i18n("profile.mfa.fallback.2")}: <code>${mfaFallback}</code></div>
                            <div class="note__text">${i18n("profile.mfa.fallback.3")?no_esc}</div>
                        </div>
                    </div>
                </#if>
                <div class="line" style="border-bottom:none">
                    <div>
                        <div class="line__label">${i18n("profile.mfa.enabled")}</div>
                    </div>
                    <a class="btn btn--danger btn--line line__ctrl" href="/dashboard/profile?mfa=disable">
                        <@icons.icon "shield"/>${i18n("profile.mfa.disable")}
                    </a>
                </div>
            </div>
        <#elseif enrollMfa>
            <p class="sheet__lead">${i18n("profile.mfa.enable.info")}</p>
            <form action="/dashboard/profile/enable-mfa" method="POST">
                <div class="sheet__body">
                    <div class="line line--stack" style="padding-top:18px;border-bottom:none">
                        <div style="display:flex;gap:22px;flex-wrap:wrap">
                            <#if qrCode??>
                                <img class="qr-code" src="data:image/png;base64,${qrCode}" alt="">
                            </#if>
                            <div style="flex:1;min-width:230px;display:flex;flex-direction:column;gap:16px">
                                <div class="field">
                                    <span class="label">${i18n("profile.totp.label")}</span>
                                    <div class="otp">
                                        <input name="otp-1" type="text" maxlength="1" pattern="\d*" inputmode="numeric" class="otp-input">
                                        <input name="otp-2" type="text" maxlength="1" pattern="\d*" inputmode="numeric" class="otp-input">
                                        <input name="otp-3" type="text" maxlength="1" pattern="\d*" inputmode="numeric" class="otp-input">
                                        <input name="otp-4" type="text" maxlength="1" pattern="\d*" inputmode="numeric" class="otp-input">
                                        <input name="otp-5" type="text" maxlength="1" pattern="\d*" inputmode="numeric" class="otp-input">
                                        <input name="otp-6" type="text" maxlength="1" pattern="\d*" inputmode="numeric" class="otp-input">
                                    </div>
                                </div>
                                <div class="actions">
                                    <button type="submit" class="btn btn--ink">
                                        <@icons.icon "shield"/>${i18n("profile.mfa.validate")}
                                    </button>
                                    <a class="btn btn--quiet btn--muted" href="/dashboard/profile">${i18n("layout.modal.add.category.cancel")}</a>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <@csrfform/>
            </form>
        <#else>
            <p class="sheet__lead">${i18n("profile.mfa.info")}</p>
            <div class="sheet__body">
                <div class="line" style="border-bottom:none">
                    <div>
                        <div class="line__label">${i18n("profile.mfa.disabled")}</div>
                    </div>
                    <a class="btn btn--ink line__ctrl" href="/dashboard/profile?mfa=enable">
                        <@icons.icon "shield"/>${i18n("profile.mfa.enable")}
                    </a>
                </div>
            </div>
        </#if>
    </div>

    <div class="sheet">
        <div class="sheet__title">${i18n("profile.language.title")}</div>
        <form action="/dashboard/profile/language" method="POST">
            <div class="sheet__body">
                <div class="line" style="border-bottom:none">
                    <div>
                        <div class="line__label">${i18n("profile.language.title")}</div>
                        <div class="line__hint">${i18n("profile.language.info")}</div>
                    </div>
                    <div class="line__ctrl" style="display:flex;gap:8px;align-items:center">
                        <select class="select" name="language" style="width:150px">
                            <#list languages as key, value>
                                <option value="${key}"<#if language == key> selected</#if>>${value}</option>
                            </#list>
                        </select>
                        <button type="submit" class="btn btn--line">${i18n("profile.language.save")}</button>
                    </div>
                </div>
            </div>
            <@csrfform/>
        </form>
    </div>

    <div class="sheet">
        <div class="sheet__title">${i18n("profile.email.title")}</div>
        <#if !confirmed>
            <div class="note note--gold" style="margin-top:14px">
                <@icons.icon "warn"/>
                <div><div class="note__text">${i18n("profile.email.notification")?no_esc}</div></div>
            </div>
        </#if>
        <form action="/dashboard/profile/change-username" method="POST" data-busy="update-email-button">
            <div class="sheet__body">
                <div class="line line--stack">
                    <div class="field">
                        <span class="label">${i18n("profile.email.label")}</span>
                        <input class="input" type="email" value="${username}" disabled>
                    </div>
                </div>
                <div class="line line--stack">
                    <div class="field">
                        <label class="label" for="new-username">${i18n("profile.email.new.label")}</label>
                        <input class="input<#if form.hasError("username")> is-invalid</#if>" type="email" id="new-username"
                               placeholder="${i18n("profile.email.placeholder")}" name="username">
                        <#if form.hasError("username")>
                            <p class="help is-invalid">${form.getError("username")}</p>
                        </#if>
                    </div>
                </div>
                <div class="line line--stack" style="border-bottom:none">
                    <div class="field">
                        <label class="label" for="email-password">${i18n("profile.email.password.label")}</label>
                        <input class="input" type="password" id="email-password" name="password"
                               placeholder="${i18n("profile.email.password.placeholder")}">
                    </div>
                    <div class="actions" style="margin-top:18px">
                        <button type="submit" class="btn btn--ink" id="update-email-button">${i18n("profile.email.button")}</button>
                    </div>
                </div>
            </div>
            <@csrfform/>
        </form>
    </div>

    <div class="sheet">
        <div class="sheet__title">${i18n("profile.password.title")}</div>
        <form action="/dashboard/profile/change-password" method="POST" data-busy="update-password-button">
            <div class="sheet__body">
                <div class="line line--stack">
                    <div class="field">
                        <label class="label" for="current-password">${i18n("profile.password.label")}</label>
                        <input class="input" type="password" id="current-password" name="password"
                               placeholder="${i18n("profile.password.placeholder")}">
                    </div>
                </div>
                <div class="line line--stack">
                    <div class="field">
                        <label class="label" for="new-password">${i18n("profile.password.new.label")}</label>
                        <input class="input<#if form.hasError("new-password")> is-invalid</#if>" type="password"
                               id="new-password" name="new-password" placeholder="${i18n("profile.password.new.placeholder")}">
                        <#if form.hasError("new-password")>
                            <p class="help is-invalid">${form.getError("new-password")}</p>
                        </#if>
                    </div>
                </div>
                <div class="line line--stack" style="border-bottom:none">
                    <div class="field">
                        <label class="label" for="confirm-password">${i18n("profile.password.confirm.label")}</label>
                        <input class="input<#if form.hasError("confirm-password")> is-invalid</#if>" type="password"
                               id="confirm-password" name="confirm-password" placeholder="${i18n("profile.password.confirm.placeholder")}">
                        <#if form.hasError("confirm-password")>
                            <p class="help is-invalid">${form.getError("confirm-password")}</p>
                        </#if>
                    </div>
                    <div class="actions" style="margin-top:18px">
                        <button type="submit" class="btn btn--ink" id="update-password-button">${i18n("profile.password.button")}</button>
                    </div>
                </div>
            </div>
            <@csrfform/>
        </form>
    </div>

    <div class="sheet">
        <div class="sheet__title">${i18n("profile.logout.devices.title")}</div>
        <div class="sheet__body">
            <div class="line" style="border-bottom:none">
                <div>
                    <div class="line__label">${i18n("profile.logout.devices.title")}</div>
                    <div class="line__hint">${i18n("profile.logout.devices.info")}</div>
                </div>
                <button class="btn btn--line line__ctrl" id="logout-devices" type="button">
                    <@icons.icon "devices"/>${i18n("profile.logout.devices.submit")}
                </button>
            </div>
        </div>
    </div>

    <div class="sheet">
        <div class="sheet__title" style="color:var(--rust)">${i18n("profile.danger.title")}</div>
        <div class="sheet__body">
            <div class="line">
                <div>
                    <div class="line__label">${i18n("profile.resync.button")}</div>
                    <div class="line__hint">${i18n("profile.resync.hint")}</div>
                </div>
                <a class="btn btn--line line__ctrl" href="/dashboard/resync">
                    <@icons.icon "sync"/>${i18n("profile.resync.button")}
                </a>
            </div>
            <div class="line" style="border-bottom:none">
                <div>
                    <div class="line__label">${i18n("profile.danger.button")}</div>
                    <div class="line__hint">${i18n("layout.modal.delete.account.body")}</div>
                </div>
                <button class="btn btn--danger btn--line line__ctrl" id="delete-account" type="button">
                    <@icons.icon "trash"/>${i18n("profile.danger.button")}
                </button>
            </div>
        </div>
    </div>
</div>
</@layout.myLayout>
