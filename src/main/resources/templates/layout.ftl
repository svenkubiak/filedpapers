<#macro myLayout title="Layout example">
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Filed Papers</title>
    <link rel="icon" type="image/x-icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png">
    <link rel="stylesheet" href="/assets/css/bulma.min.css">
    <link rel="stylesheet" href="/assets/css/all.min.css">
    <link rel="stylesheet" href="/assets/css/app.min.css">
</head>
<body>
<aside class="sidebar">
    <div class="sidebar-content">
        <p class="menu-label menu-label-with-icon">
            ${i18n("layout.menu.label")}
            <span class="icon is-small" style="cursor: pointer;" id="add-category-button">
                <i class="fas fa-plus"></i>
            </span>
        </p>
        <ul class="menu-list">
            <#list categories as category>
                <#assign slug = category.name?lower_case>
                <li>
                    <#if slug == "inbox">
                        <a href="/dashboard" data-category="${slug}" data-uid="${category.uid}" <#if slug == active>class="is-active"</#if>>
                    <#else>
                        <a href="/dashboard/${category.uid}" data-category="${slug}" data-uid="${category.uid}" <#if slug == active>class="is-active"</#if>>
                    </#if>
                    <span class="icon">
                    <#if slug == "inbox">
                        <i class="fas fa-inbox"></i>
                    <#elseif slug == "trash">
                        <i class="fas fa-trash"></i>
                    <#else>
                        <i class="fas fa-folder"></i>
                    </#if>
                    </span>
                        ${category.name}
                        <span class="tag is-rounded is-pulled-right">${category.count}</span>
                    </a>
                </li>
            </#list>
        </ul>
    </div>
    <div class="sidebar-footer">
        <div class="buttons">
            <a href="/dashboard/profile" class="button is-light is-fullwidth mb-2<#if active == "profile"> is-active</#if>">
                <span class="icon">
                    <i class="fas fa-user-cog"></i>
                </span>
                <span>${i18n("layout.menu.profile")}</span>
            </a>
            <a href="/dashboard/io" class="button is-light is-fullwidth mb-2<#if active == "io"> is-active</#if>">
                <span class="icon">
                    <i class="fas fa-sync"></i>
                </span>
                <span>${i18n("layout.menu.io")}</span>
            </a>
            <a href="/auth/logout" class="button is-danger is-light is-fullwidth">
                <span class="icon">
                    <i class="fas fa-sign-out-alt"></i>
                </span>
                <span>${i18n("layout.menu.logout")}</span>
            </a>
        </div>
    </div>
</aside>
<div class="main-content">
    <button class="button is-primary is-rounded fab-button" id="fab-add-bookmark">
        <span class="icon">
            <i class="fas fa-plus"></i>
        </span>
        <span>${i18n("layout.add.bookmark")}</span>
    </button>
   <#nested/>
</div>
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
<div class="toast-container"></div>
<div id="i18n-js"
     data-error='${i18n("toast.error")}'
     data-bookmark-moved-success='${i18n("js.bookmark.moved")}'
     data-category-deleted-success='${i18n("js.category.deleted")}'
     data-trash-emptied-euccess='${i18n("js.trash.emptied")}'
     data-bookmark-deleted-success='${i18n("js.bookmark.deleted")}'
     data-category-created-success='${i18n("js.category.created")}'
     data-bookmark-created-success='${i18n("js.bookmark.created")}'>
</div>
<script src="/assets/js/app.min.js"></script>
<#if flash.toastsuccess??>
<script>showToast("${flash.toastsuccess}");</script>
</#if>
<#if flash.toasterror??>
<script>showToast("${flash.toasterror}", "error");</script>
</#if>
</body>
</html>
</#macro>
