<#macro modals>
<div class="modal" id="add-category-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">${i18n("layout.modal.add.category.title")}</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <div class="field">
                <label class="label">${i18n("layout.modal.add.category.name")}</label>
                <div class="control">
                    <input class="input" type="text" name="category" id="category">
                </div>
            </div>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">${i18n("layout.modal.add.category.cancel")}</button>
                <button class="button is-primary" id="add-category-submit">${i18n("layout.modal.add.category.add")}</button>
            </div>
        </footer>
    </div>
</div>
<div class="modal" id="delete-account-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">${i18n("layout.modal.delete.account.title")}</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <p>${i18n("layout.modal.delete.account.body")}</p>
        </section>
        <form action="/dashboard/profile/delete-account" method="POST">
            <section class="modal-card-body">
                <div class="field">
                    <label class="label">${i18n("layout.modal.delete.account.password")}</label>
                    <div class="control">
                        <input class="input" type="password" name="confirmPassword" placeholder="${i18n("layout.modal.delete.account.placeholder")}">
                    </div>
                </div>
            </section>
            <footer class="modal-card-foot">
                <div class="is-flex is-justify-content-space-between" style="width: 100%">
                    <button class="button">${i18n("layout.modal.delete.account.cancel")}</button>
                    <input type="submit" class="button is-danger" id="add-category-submit" value="${i18n("layout.modal.delete.account.delete")}">
                </div>
            </footer>
        </form>
    </div>
</div>
<div class="modal" id="delete-confirm-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">${i18n("layout.modal.delete.bookmark.title")}</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <p>${i18n("layout.modal.delete.bookmark.body")}</p>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">${i18n("layout.modal.delete.bookmark.cancel")}</button>
                <button class="button is-danger" id="confirm-delete">${i18n("layout.modal.delete.bookmark.delete")}</button>
            </div>
        </footer>
    </div>
</div>
<div class="modal" id="delete-category-confirm-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">${i18n("layout.modal.delete.category.title")}</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <p>${i18n("layout.modal.delete.category.body")}</p>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">${i18n("layout.modal.delete.category.cancel")}</button>
                <button class="button is-danger" id="confirm-category-delete">${i18n("layout.modal.delete.category.delete")}</button>
            </div>
        </footer>
    </div>
</div>
<div class="modal" id="empty-trash-confirm-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">${i18n("layout.modal.trash.title")}</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <p>${i18n("layout.modal.trash.body")}</p>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">${i18n("layout.modal.trash.cancel")}</button>
                <button class="button is-danger" id="confirm-empty-trash">${i18n("layout.modal.trash.empty")}</button>
            </div>
        </footer>
    </div>
</div>
<div class="modal" id="logout-devices-confirm-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">${i18n("layout.modal.logout.devices.title")}</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <p>${i18n("layout.modal.logout.devices.body")}</p>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">${i18n("layout.modal.logout.devices.cancel")}</button>
                <button class="button is-danger" id="confirm-logout-devices">${i18n("layout.modal.logout.devices.logout")}</button>
            </div>
        </footer>
    </div>
</div>
<div class="modal" id="add-bookmark-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">${i18n("layout.modal.add.bookmark.title")}</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <div class="field">
                <label class="label">${i18n("layout.modal.add.bookmark.url")}</label>
                <div class="control">
                    <input class="input" type="url" name="bookmark-url" id="bookmark-url" placeholder="https://example.com">
                </div>
            </div>
            <div class="field">
                <label class="label">${i18n("layout.modal.add.bookmark.category")}</label>
                <div class="control">
                    <div class="select is-fullwidth">
                        <select name="bookmark-category" id="bookmark-category">
                            <#list categories as category>
                                <#assign slug = category.name?lower_case>
                                <#if slug != "trash">
                                    <option value="${category.uid}">${category.name}</option>
                                </#if>
                            </#list>
                        </select>
                    </div>
                </div>
            </div>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">${i18n("layout.modal.add.bookmark.cancel")}</button>
                <button class="button is-primary" id="confirm-add-bookmark">${i18n("layout.modal.add.bookmark.add")}</button>
            </div>
        </footer>
    </div>
</div>
</#macro>