<#macro modals>
<#import "_icons.ftl" as icons>

<div class="veil" id="veil-search">
    <div class="palette">
        <div class="palette__in">
            <@icons.icon "search"/>
            <input type="search" id="search-input" autocomplete="off" spellcheck="false"
                   placeholder="${i18n("dashboard.search")}">
            <kbd>esc</kbd>
        </div>
        <div class="palette__list" id="search-results"></div>
        <div class="palette__foot">
            <span><kbd>↑</kbd><kbd>↓</kbd> ${i18n("palette.navigate")}</span>
            <span><kbd>↵</kbd> ${i18n("palette.open")}</span>
        </div>
    </div>
</div>

<div class="veil veil--center" id="add-bookmark-modal">
    <div class="dialog">
        <div class="dialog__body">
            <div class="dialog__title">${i18n("layout.modal.add.bookmark.title")}</div>
            <p class="dialog__text">${i18n("layout.modal.add.bookmark.hint")}</p>
            <div style="display:flex;flex-direction:column;gap:18px;margin-top:20px">
                <div class="field">
                    <label class="label" for="bookmark-url">${i18n("layout.modal.add.bookmark.url")}</label>
                    <input class="input" type="url" name="bookmark-url" id="bookmark-url" placeholder="https://example.com">
                </div>
                <div class="field">
                    <label class="label" for="bookmark-category">${i18n("layout.modal.add.bookmark.category")}</label>
                    <select class="select" name="bookmark-category" id="bookmark-category">
                        <#list categories as category>
                            <#assign slug = category.name?lower_case>
                            <#if slug != "trash">
                                <option value="${category.uid}"<#if categoryUid?? && categoryUid == category.uid> selected</#if>>
                                    <#if slug == "inbox">${i18n("layout.inbox.name")}<#else>${category.name}</#if>
                                </option>
                            </#if>
                        </#list>
                    </select>
                </div>
            </div>
        </div>
        <div class="dialog__foot">
            <button class="btn btn--quiet btn--muted" data-close type="button">${i18n("layout.modal.add.bookmark.cancel")}</button>
            <button class="btn btn--ink" id="confirm-add-bookmark" data-confirm type="button">${i18n("layout.modal.add.bookmark.add")}</button>
        </div>
    </div>
</div>

<div class="veil veil--center" id="add-category-modal">
    <div class="dialog">
        <div class="dialog__body">
            <div class="dialog__title">${i18n("layout.modal.add.category.title")}</div>
            <p class="dialog__text">${i18n("layout.modal.add.category.hint")}</p>
            <div class="field" style="margin-top:20px">
                <label class="label" for="category">${i18n("layout.modal.add.category.name")}</label>
                <input class="input" type="text" name="category" id="category" maxlength="32">
            </div>
        </div>
        <div class="dialog__foot">
            <button class="btn btn--quiet btn--muted" data-close type="button">${i18n("layout.modal.add.category.cancel")}</button>
            <button class="btn btn--ink" id="add-category-submit" data-confirm type="button">${i18n("layout.modal.add.category.add")}</button>
        </div>
    </div>
</div>

<div class="veil veil--center" id="rename-category-modal">
    <div class="dialog">
        <div class="dialog__body">
            <div class="dialog__title">${i18n("layout.modal.rename.category.title")}</div>
            <div class="field" style="margin-top:20px">
                <label class="label" for="existing-category">${i18n("layout.modal.rename.category.name")}</label>
                <input class="input" type="text" name="category" id="existing-category" maxlength="32">
            </div>
        </div>
        <div class="dialog__foot">
            <button class="btn btn--quiet btn--muted" data-close type="button">${i18n("layout.modal.rename.category.cancel")}</button>
            <button class="btn btn--ink" id="rename-category-submit" data-confirm type="button">${i18n("layout.modal.rename.category.add")}</button>
        </div>
    </div>
</div>

<div class="veil veil--center" id="bulk-move-modal">
    <div class="dialog">
        <div class="dialog__body">
            <div class="dialog__title">${i18n("layout.modal.bulk.move.title")}</div>
            <p class="dialog__text" id="bulk-move-count"></p>
            <div class="field" style="margin-top:20px">
                <label class="label" for="bulk-move-category">${i18n("layout.modal.add.bookmark.category")}</label>
                <select class="select" id="bulk-move-category">
                    <#list categories as category>
                        <#assign slug = category.name?lower_case>
                        <#if slug != "trash" && !(categoryUid?? && categoryUid == category.uid)>
                            <option value="${category.uid}">
                                <#if slug == "inbox">${i18n("layout.inbox.name")}<#else>${category.name}</#if>
                            </option>
                        </#if>
                    </#list>
                </select>
            </div>
        </div>
        <div class="dialog__foot">
            <button class="btn btn--quiet btn--muted" data-close type="button">${i18n("layout.modal.bulk.move.cancel")}</button>
            <button class="btn btn--ink" id="confirm-bulk-move" data-confirm type="button">${i18n("layout.modal.bulk.move.submit")}</button>
        </div>
    </div>
</div>

<div class="veil veil--center" id="delete-category-confirm-modal">
    <div class="dialog">
        <div class="dialog__body">
            <div class="dialog__title">${i18n("layout.modal.delete.category.title")}</div>
            <p class="dialog__text">${i18n("layout.modal.delete.category.body")}</p>
        </div>
        <div class="dialog__foot">
            <button class="btn btn--quiet btn--muted" data-close type="button">${i18n("layout.modal.delete.category.cancel")}</button>
            <button class="btn btn--danger btn--ink" id="confirm-category-delete" data-confirm type="button">${i18n("layout.modal.delete.category.delete")}</button>
        </div>
    </div>
</div>

<div class="veil veil--center" id="empty-trash-confirm-modal">
    <div class="dialog">
        <div class="dialog__body">
            <div class="dialog__title">${i18n("layout.modal.trash.title")}</div>
            <p class="dialog__text">${i18n("layout.modal.trash.body")}</p>
        </div>
        <div class="dialog__foot">
            <button class="btn btn--quiet btn--muted" data-close type="button">${i18n("layout.modal.trash.cancel")}</button>
            <button class="btn btn--danger btn--ink" id="confirm-empty-trash" data-confirm type="button">${i18n("layout.modal.trash.empty")}</button>
        </div>
    </div>
</div>

<div class="veil veil--center" id="bulk-delete-confirm-modal">
    <div class="dialog">
        <div class="dialog__body">
            <div class="dialog__title">${i18n("layout.modal.bulk.delete.title")}</div>
            <p class="dialog__text" id="bulk-delete-count"></p>
        </div>
        <div class="dialog__foot">
            <button class="btn btn--quiet btn--muted" data-close type="button">${i18n("layout.modal.bulk.delete.cancel")}</button>
            <button class="btn btn--danger btn--ink" id="confirm-bulk-delete" data-confirm type="button">${i18n("layout.modal.bulk.delete.submit")}</button>
        </div>
    </div>
</div>

<div class="veil veil--center" id="logout-devices-confirm-modal">
    <div class="dialog">
        <div class="dialog__body">
            <div class="dialog__title">${i18n("layout.modal.logout.devices.title")}</div>
            <p class="dialog__text">${i18n("layout.modal.logout.devices.body")}</p>
        </div>
        <div class="dialog__foot">
            <button class="btn btn--quiet btn--muted" data-close type="button">${i18n("layout.modal.logout.devices.cancel")}</button>
            <button class="btn btn--danger btn--ink" id="confirm-logout-devices" data-confirm type="button">${i18n("layout.modal.logout.devices.logout")}</button>
        </div>
    </div>
</div>

<div class="veil veil--center" id="delete-account-modal">
    <div class="dialog">
        <form action="/dashboard/profile/delete-account" method="POST">
            <div class="dialog__body">
                <div class="dialog__title">${i18n("layout.modal.delete.account.title")}</div>
                <p class="dialog__text">${i18n("layout.modal.delete.account.body")}</p>
                <div class="field" style="margin-top:20px">
                    <label class="label" for="confirmPassword">${i18n("layout.modal.delete.account.password")}</label>
                    <input class="input" type="password" name="confirmPassword" id="confirmPassword"
                           placeholder="${i18n("layout.modal.delete.account.placeholder")}">
                </div>
            </div>
            <div class="dialog__foot">
                <button class="btn btn--quiet btn--muted" data-close type="button">${i18n("layout.modal.delete.account.cancel")}</button>
                <button class="btn btn--danger btn--ink" type="submit">${i18n("layout.modal.delete.account.delete")}</button>
            </div>
            <@csrfform/>
        </form>
    </div>
</div>
</#macro>
