<#import "../layout.ftl" as layout>
<@layout.myLayout "Layout">
<nav class="breadcrumb" aria-label="breadcrumbs">
    <ul>
        <li class="is-active"><a aria-current="page"><span class="icon is-small"><i class="fas fa-bookmark"></i></span>${breadcrumb}</a></li>
        <#if active == 'trash'>
        <li><span class="icon empty-trash"><i class="fas fa-trash-alt"></i></span></li>
        <#elseif active == 'inbox'>
        <#else>
        <li><span class="icon category-trash" data-uid="${categoryUid}"><i class="fas fa-folder-minus"></i></span></li>
        </#if>
    </ul>
</nav>
<div class="columns is-multiline">
    <#list items?sort_by("sort")?reverse as item>
        <div class="column is-one-quarter">
            <div class="card" data-uid="${item.uid}" data-category="${categoryUid}">
                <a href="${item.url}" target="_blank" class="card-link">
                    <div class="card-image">
                        <div class="image-container">
                            <div class="spinner"></div>
                            <img src="${item.image}" class="image-with-fallback" alt="Thumbnail">
                        </div>
                    </div>
                </a>
                <div class="card-content">
                    <div class="card-title-wrapper is-flex is-justify-content-space-between is-align-items-flex-start">
                        <p class="card-title">
                            <a href="${item.url}" target="_blank" class="card-link-no-decoration">${item.title}</a>
                        </p>
                        <div class="is-flex">
                            <span class="icon card-move" data-tooltip="Drag&Drop bookmark to another category">
                                <i class="fas fa-folder-open dragging" draggable="true" data-uid="${item.uid}"></i>
                            </span>
                                <span class="icon card-trash" data-tooltip="Delete bookmark">
                                <i class="fas fa-trash-alt"></i>
                            </span>
                        </div>
                    </div>
                    <div class="card-meta" >
                        <span class="card-domain"><a href="${item.url}" target="_blank">${item.url?truncate(50, '...')}</a></span>
                        <span class="card-added">Added ${item.added}</span>
                    </div>
                </div>
            </div>
        </div>
    <#else>
        <#if active?? && active == "trash">
            <div class="column is-half">
                <h2 class="is-size-3">Nothing in Trash</h2>
                <p>Items you delete will appear here</p>
            </div>
        <#else>
            <div class="column is-half">
                <h2 class="is-size-3">No bookmarks yet</h2>
                <p>Bookmarks in this category will appear here</p>
            </div>
        </#if>
    </#list>
</div>
</@layout.myLayout>
