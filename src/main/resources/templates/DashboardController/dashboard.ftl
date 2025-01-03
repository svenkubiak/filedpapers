<#import "../layout.ftl" as layout>
<@layout.myLayout "Layout">
<nav class="breadcrumb" aria-label="breadcrumbs">
    <ul>
        <li><a href="/dashboard"><span class="icon is-small"><i class="fas fa-bookmark"></i></span>Bookmarks</a></li>
        <li class="is-active"><a aria-current="page">${breadcrumb}</a></li>
        <#if active == 'trash'>
        <li><span class="icon empty-trash"><i class="fas fa-trash-alt"></i></span></li>
        <#else>
        <li><span class="icon category-trash" data-uid="${categoryUid}"><i class="fas fa-trash-alt"></i></span></li>
        </#if>
    </ul>
</nav>

<div class="columns is-multiline">
    <#list items?sort_by("sort") as item>
        <div class="column is-one-quarter">
            <div class="card" data-uid="${item.uid}">
                <a href="${item.url}" target="_blank" class="card-link">
                    <div class="card-image">
                        <img src="${item.image}" alt="Thumbnail">
                    </div>
                    <div class="card-content">
                        <div class="card-title-wrapper">
                            <p class="card-title" draggable="true" data-uid="${item.uid}">${item.title}</p>
                            <span class="icon card-trash">
                                <i class="fas fa-trash-alt"></i>
                            </span>
                        </div>
                        <div class="card-meta">
                            <span class="card-domain"><a href="${item.url}" target="_blank">${item.url}</a></span>
                            <span class="card-added">Added ${item.added}</span>
                        </div>
                    </div>
                </a>
            </div>
        </div>
    <#else>
    <div class="column is-half">
            <h2 class="is-size-3">No bookmarks yet</h2>
            <p>Bookmarks in this category will appear here</p>
        </div>
    </#list>
</div>
</@layout.myLayout>
