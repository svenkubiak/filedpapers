<#macro myLayout title="Filed Papers">
<#import "_modals.ftl" as modal>
<#import "_icons.ftl" as icons>
<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Filed Papers</title>
    <link rel="icon" type="image/x-icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png">
    <#--
      Runs before the stylesheet paints anything. Without it a dark theme
      arrives one frame late and every page load flashes white.
    -->
    <script>
        (function () {
            var stored = null;
            try { stored = localStorage.getItem('theme'); } catch (e) {}
            var dark = stored ? stored === 'dark'
                : window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
            document.documentElement.dataset.theme = dark ? 'dark' : 'light';
        })();
    </script>
    <link rel="stylesheet" href="/assets/css/app.css?v=${assetVersion}">
</head>
<body>
<@icons.sprite/>

<div class="app">
    <aside class="sidebar" id="sidebar">
        <div class="sidebar__top">
            <a class="wordmark" href="/dashboard">
                <span class="wordmark__name">Filed Papers<span class="wordmark__dot">.</span></span>
                <span class="wordmark__v">${version}</span>
            </a>

            <button class="searchbar" id="open-search" type="button">
                <@icons.icon "search"/>
                <span class="searchbar__label">${i18n("dashboard.search")}</span>
                <kbd>⌘K</kbd>
            </button>

            <button class="btn btn--ink btn--block" id="add-bookmark" type="button">
                <@icons.icon "plus"/>${i18n("layout.add.bookmark")}
            </button>
        </div>

        <nav class="sidebar__nav scroll" id="nav">
            <div class="navlabel">
                <span>${i18n("layout.nav.library")}</span>
                <button class="btn btn--quiet btn--icon btn--sm" id="add-category-button" type="button"
                        aria-label="${i18n("layout.modal.add.category.title")}">
                    <@icons.icon "plus"/>
                </button>
            </div>

            <#list categories as category>
                <#assign slug = category.name?lower_case>
                <#assign current = categoryUid?? && categoryUid == category.uid>
                <#if slug != "trash">
                    <a class="navitem<#if current> is-active</#if>"
                       href="<#if slug == "inbox">/dashboard<#else>/dashboard/${category.uid}</#if>"
                       data-uid="${category.uid}" data-category="${slug}">
                        <#if slug == "inbox">
                            <@icons.icon "inbox"/>
                        <#elseif current>
                            <@icons.icon "folder-open"/>
                        <#else>
                            <@icons.icon "folder"/>
                        </#if>
                        <span class="navitem__t"><#if slug == "inbox">${i18n("layout.inbox.name")}<#else>${category.name}</#if></span>
                        <span class="navitem__n">${category.count}</span>
                    </a>
                </#if>
            </#list>

            <#list categories as category>
                <#assign slug = category.name?lower_case>
                <#if slug == "trash">
                    <div class="navsep"></div>
                    <a class="navitem<#if categoryUid?? && categoryUid == category.uid> is-active</#if>"
                       href="/dashboard/${category.uid}" data-uid="${category.uid}" data-category="trash">
                        <@icons.icon "trash"/>
                        <span class="navitem__t">${i18n("layout.trash.name")}</span>
                        <span class="navitem__n">${category.count}</span>
                    </a>
                </#if>
            </#list>
        </nav>

        <div class="sidebar__foot">
            <div class="menu" id="account-menu">
                <a class="menu__item<#if active == "profile"> is-active</#if>" href="/dashboard/profile">
                    <@icons.icon "user"/>${i18n("layout.menu.profile")}
                </a>
                <a class="menu__item<#if active == "io"> is-active</#if>" href="/dashboard/io">
                    <@icons.icon "sync"/>${i18n("layout.menu.io")}
                </a>
                <a class="menu__item<#if active == "about"> is-active</#if>" href="/dashboard/about">
                    <@icons.icon "info"/>${i18n("layout.menu.about")}
                </a>
                <div class="menu__sep"></div>
                <button class="menu__item" id="menu-theme" type="button">
                    <@icons.icon "moon"/>${i18n("layout.menu.theme")}<kbd class="menu__kbd">⌘J</kbd>
                </button>
                <button class="menu__item" id="menu-search" type="button">
                    <@icons.icon "command"/>${i18n("layout.menu.palette")}<kbd class="menu__kbd">⌘K</kbd>
                </button>
                <div class="menu__sep"></div>
                <a class="menu__item menu__item--danger" href="/auth/logout">
                    <@icons.icon "logout"/>${i18n("layout.menu.logout")}
                </a>
            </div>

            <button class="account" id="account" type="button" aria-haspopup="true" aria-expanded="false">
                <span class="avatar">${username?substring(0, 2)?upper_case}</span>
                <span class="account__txt">
                    <span class="account__mail">${username}</span>
                    <#assign bookmarkCount = 0>
                    <#assign categoryCount = 0>
                    <#list categories as c>
                        <#if c.name?lower_case != "trash">
                            <#assign bookmarkCount = bookmarkCount + c.count?number>
                            <#assign categoryCount = categoryCount + 1>
                        </#if>
                    </#list>
                    <span class="account__sub">${bookmarkCount} ${i18n("layout.account.bookmarks")} · ${categoryCount} ${i18n("layout.account.categories")}</span>
                </span>
                <@icons.icon "chevrons"/>
            </button>
        </div>
    </aside>

    <div class="main">
        <div class="mobar">
            <button class="btn btn--quiet btn--icon" id="burger" type="button"
                    aria-label="${i18n("layout.nav.library")}">
                <@icons.icon "menu"/>
            </button>
            <span class="wordmark__name" style="font-size:18px">Filed Papers<span class="wordmark__dot">.</span></span>
            <span style="flex:1"></span>
            <button class="btn btn--quiet btn--icon" id="open-search-sm" type="button">
                <@icons.icon "search"/>
            </button>
            <button class="btn btn--ink btn--icon" id="add-bookmark-sm" type="button">
                <@icons.icon "plus"/>
            </button>
        </div>

        <main class="page scroll">
            <div class="page__inner">
                <#nested/>
            </div>
        </main>
    </div>
</div>

<@modal.modals/>

<div class="toasts" id="toasts"></div>

<div id="i18n-js"
     class="is-hidden"
     data-error='${i18n("toast.error")}'
     data-bookmark-moved-success='${i18n("js.bookmark.moved")}'
     data-category-deleted-success='${i18n("js.category.deleted")}'
     data-category-renamed-success='${i18n("js.category.renamed")}'
     data-trash-emptied-success='${i18n("js.trash.emptied")}'
     data-bookmark-deleted-success='${i18n("js.bookmark.deleted")}'
     data-category-created-success='${i18n("js.category.created")}'
     data-bookmark-created-success='${i18n("js.bookmark.created")}'
     data-logout-devices-success='${i18n("js.logout.devices.success")}'
     data-archived-success='${i18n("js.archived.success")}'
     data-items-moved-success='${i18n("js.items.moved")}'
     data-items-deleted-success='${i18n("js.items.deleted")}'
     data-selected-one='${i18n("js.selected.one")}'
     data-selected-many='${i18n("js.selected.many")}'
     data-search-empty='${i18n("js.search.empty")}'
     data-search-hint='${i18n("js.search.hint")}'
     data-search-bookmarks='${i18n("js.search.bookmarks")}'
     data-search-categories='${i18n("js.search.categories")}'
     data-search-actions='${i18n("js.search.actions")}'
     data-search-action-add='${i18n("js.search.action.add")}'
     data-search-action-category='${i18n("js.search.action.category")}'
     data-search-action-theme='${i18n("js.search.action.theme")}'
     data-items-copied-success='${i18n("js.items.copied")}'
     data-bookmark='${i18n("dashboard.bookmark")}'
     data-bookmarks='${i18n("dashboard.bookmarks")}'>
</div>
<div id="x-csrf-token" class="is-hidden" data-csrf-token='<@csrftoken/>'></div>

<script src="/assets/js/api.js?v=${assetVersion}"></script>
<script src="/assets/js/app.js?v=${assetVersion}"></script>
<#if flash.toastsuccess??>
<script>showToast("${flash.toastsuccess}");</script>
</#if>
<#if flash.toasterror??>
<script>showToast("${flash.toasterror}", "error");</script>
</#if>
</body>
</html>
</#macro>
