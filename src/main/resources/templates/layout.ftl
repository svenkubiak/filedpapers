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
            Overview
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
                <span>Profile</span>
            </a>
            <a href="/dashboard/io" class="button is-light is-fullwidth mb-2<#if active == "io"> is-active</#if>">
                <span class="icon">
                    <i class="fas fa-sync"></i>
                </span>
                <span>Import/Export</span>
            </a>
            <a href="/auth/logout" class="button is-danger is-light is-fullwidth">
                <span class="icon">
                    <i class="fas fa-sign-out-alt"></i>
                </span>
                <span>Logout</span>
            </a>
        </div>
    </div>
</aside>
<div class="main-content">
    <button class="button is-primary is-rounded fab-button" id="fab-add-bookmark">
        <span class="icon">
            <i class="fas fa-plus"></i>
        </span>
        <span>Add Bookmark</span>
    </button>
   <#nested/>
</div>
<div class="modal" id="add-category-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">Add category</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <div class="field">
                <label class="label">Name</label>
                <div class="control">
                    <input class="input" type="text" name="category" id="category">
                </div>
            </div>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">Cancel</button>
                <button class="button is-primary" id="add-category-submit">Add</button>
            </div>
        </footer>
    </div>
</div>
<div class="modal" id="delete-account-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">Delete account</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <p>Are you sure you want to delete your account? All your data will be permanently deleted, and this action cannot be undone.</p>
        </section>
        <form action="/dashboard/profile/delete-account" method="POST">
        <section class="modal-card-body">
            <div class="field">
                <label class="label">Password</label>
                <div class="control">
                    <input class="input" type="password" name="confirmPassword" placeholder="To delete your account, type in your password">
                </div>
            </div>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">Cancel</button>
                <input type="submit" class="button is-danger" id="add-category-submit" value="Delete account">
            </div>
        </footer>
        </form>
    </div>
</div>
<div class="modal" id="delete-confirm-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">Delete Bookmark</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <p>Are you sure you want to delete this bookmark? Deleted bookmarks will be moves to trash.</p>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">Cancel</button>
                <button class="button is-danger" id="confirm-delete">Delete</button>
            </div>
        </footer>
    </div>
</div>
<div class="modal" id="delete-category-confirm-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">Delete category</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <p>Are you sure you want to delete this category? All items in this category will be moved to trash.</p>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">Cancel</button>
                <button class="button is-danger" id="confirm-category-delete">Delete</button>
            </div>
        </footer>
    </div>
</div>
<div class="modal" id="empty-trash-confirm-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">Empty trash</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <p>Are you sure you want to empty the trash? All items will be irrevocably deleted.</p>
        </section>
        <footer class="modal-card-foot">
            <div class="is-flex is-justify-content-space-between" style="width: 100%">
                <button class="button">Cancel</button>
                <button class="button is-danger" id="confirm-empty-trash">Empty</button>
            </div>
        </footer>
    </div>
</div>
<div class="modal" id="add-bookmark-modal">
    <div class="modal-background"></div>
    <div class="modal-card">
        <header class="modal-card-head">
            <p class="modal-card-title">Add Bookmark</p>
            <button class="delete" aria-label="close"></button>
        </header>
        <section class="modal-card-body">
            <div class="field">
                <label class="label">URL</label>
                <div class="control">
                    <input class="input" type="url" name="bookmark-url" id="bookmark-url" placeholder="https://example.com">
                </div>
            </div>
            <div class="field">
                <label class="label">Category</label>
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
                <button class="button">Cancel</button>
                <button class="button is-primary" id="confirm-add-bookmark">Add</button>
            </div>
        </footer>
    </div>
</div>
<div class="toast-container"></div>
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
