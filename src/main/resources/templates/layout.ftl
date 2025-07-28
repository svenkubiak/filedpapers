<#macro myLayout title="Layout example">
<#import "_modals.ftl" as modal>
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
                        <#if slug == "inbox">
                            ${i18n("layout.inbox.name")}
                        <#elseif slug == "trash">
                            ${i18n("layout.trash.name")}
                        <#else>
                            ${category.name?truncate(14, '...')}
                        </#if>
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
            <a href="/dashboard/about" class="button is-light is-fullwidth mb-2<#if active == "about"> is-active</#if>">
                <span class="icon">
                    <i class="fas fa-info-circle"></i>
                </span>
                <span>${i18n("layout.menu.about")}</span>
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
    <button class="button is-primary is-rounded fab-button" id="add-bookmark">
        <span class="icon">
            <i class="fas fa-plus"></i>
        </span>
        <span>${i18n("layout.add.bookmark")}</span>
    </button>
   <#nested/>
</div>
<@modal.modals />
<div class="toast-container"></div>
<div id="i18n-js"
     data-error='${i18n("toast.error")}'
     data-bookmark-moved-success='${i18n("js.bookmark.moved")}'
     data-category-deleted-success='${i18n("js.category.deleted")}'
     data-category-renamed-success='${i18n("js.category.renamed")}'
     data-trash-emptied-success='${i18n("js.trash.emptied")}'
     data-bookmark-deleted-success='${i18n("js.bookmark.deleted")}'
     data-category-created-success='${i18n("js.category.created")}'
     data-bookmark-created-success='${i18n("js.bookmark.created")}'
     data-logout-devices-success='${i18n("js.logout.devices.success")}'
     data-archived-success='${i18n("js.archived.success")}'>
</div>
<script src="/assets/js/axios.min.js"></script>
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
