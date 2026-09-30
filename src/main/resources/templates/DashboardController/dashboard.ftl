<#import "../layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "Dashboard">
<#assign isTrash = active == "trash">
<#assign isInbox = active == "inbox">
<#-- "3 days" or "5 hours", depending on application.trash.retention -->
<#assign retention = trashRetention.value + " " + i18n("dashboard.trash.unit." + trashRetention.unit)>

<div class="page-head">
    <div>
        <h1 class="page-head__title"><#if isInbox>${i18n("layout.inbox.name")}<#elseif isTrash>${i18n("layout.trash.name")}<#else>${breadcrumb}</#if></h1>
        <div class="page-head__meta">
            <span>${items?size} <#if items?size == 1>${i18n("dashboard.bookmark")}<#else>${i18n("dashboard.bookmarks")}</#if></span>
            <#if isTrash>
                <span class="dot">·</span><span>${i18n("dashboard.trash.hint")?replace("{0}", retention)}</span>
            </#if>
        </div>
    </div>

    <div class="page-head__tools">
        <#if items?has_content>
            <button class="btn btn--quiet btn--sm btn--muted" id="pick-start" type="button">
                <@icons.icon "check-square"/>${i18n("dashboard.select")}
            </button>
            <button class="btn btn--quiet btn--icon btn--sm" id="view-list" type="button" data-tip="${i18n("dashboard.view.list")}">
                <@icons.icon "list"/>
            </button>
            <button class="btn btn--quiet btn--icon btn--sm" id="view-grid" type="button" data-tip="${i18n("dashboard.view.grid")}">
                <@icons.icon "grid"/>
            </button>
        </#if>
        <#if isTrash>
            <button class="btn btn--danger btn--line btn--sm empty-trash" type="button">
                <@icons.icon "trash"/>${i18n("dashboard.empty.trash")}
            </button>
        <#elseif !isInbox>
            <button class="btn btn--quiet btn--sm btn--muted category-rename" type="button"
                    data-uid="${categoryUid}" data-name="${breadcrumb}">
                <@icons.icon "pencil"/>${i18n("dashboard.rename.category")}
            </button>
            <button class="btn btn--quiet btn--sm btn--muted btn--danger category-trash" type="button" data-uid="${categoryUid}">
                <@icons.icon "trash"/>${i18n("dashboard.delete.category")}
            </button>
        </#if>
    </div>
</div>

<#if flash.warning??>
    <div class="note note--gold" style="margin-bottom:22px">
        <@icons.icon "warn"/>
        <div><div class="note__title">${flash.warning}</div></div>
    </div>
</#if>

<#if isTrash && items?has_content>
    <div class="note note--gold" style="margin-bottom:22px">
        <@icons.icon "warn"/>
        <div>
            <div class="note__title">${i18n("dashboard.trash.note.title")?replace("{0}", retention)}</div>
            <div class="note__text">${i18n("dashboard.trash.note.text")}</div>
        </div>
    </div>
</#if>

<#if items?has_content>
    <div class="shelf" id="shelf">
        <#list items?sort_by("sort")?reverse as item>
            <article class="item" draggable="true" data-uid="${item.uid}" data-category="${categoryUid}">
                <div class="item__frame">
                    <#-- Without a scraped preview the placeholder image would be a white
                         rectangle in a dark theme; an initial on a tinted frame is calmer
                         and carries the same amount of information. -->
                    <#assign preview = item.image?? && !item.image?ends_with("placeholder.svg")>
                    <#if preview>
                        <div class="item__art"><img src="${item.image}" alt="" loading="lazy"></div>
                    <#else>
                        <#assign host = ((item.domain!"")?has_content)?then(item.domain, item.url?remove_beginning("https://")?remove_beginning("http://"))?remove_beginning("www.")>
                        <div class="item__art item__art--blank">
                            <span><#if host?has_content>${host?substring(0, 1)?upper_case}<#else>·</#if></span>
                        </div>
                    </#if>
                    <div class="item__scrim"></div>
                </div>
                <#-- Deliberately a sibling of the frame, not a child: in the compact
                     view these sit at the end of the row, and inside the frame they
                     would be clipped by its overflow and painted over by the link. -->
                <span class="checkbox item__pick" aria-checked="false"><@icons.icon "check"/></span>
                <div class="item__acts">
                    <button class="act act--grab item-move" type="button" tabindex="-1"
                            data-tip="${i18n("dashboard.card.drag.tooltip")}">
                        <@icons.icon "move"/>
                    </button>
                    <#if !isTrash>
                        <button class="act item-archive" type="button" data-uid="${item.uid}"
                                data-tip="${i18n("dashboard.card.archive.tooltip")}">
                            <@icons.icon "archive"/>
                        </button>
                        <button class="act act--danger item-trash" type="button"
                                data-tip="${i18n("dashboard.card.delete.tooltip")}">
                            <@icons.icon "trash"/>
                        </button>
                    </#if>
                </div>
                <div class="item__text">
                    <h3 class="item__title">
                        <a class="item__link" href="${item.url}" target="_blank" rel="noopener">${item.title}</a>
                    </h3>
                    <div class="item__by">
                        <span class="item__host"><#if item.domain?? && item.domain?has_content>${item.domain}<#else>${item.url}</#if></span>
                        <span class="dot">·</span>
                        <#if item.deleteAt??>
                            <#-- prettytime words a future date as "in 3 days" -->
                            <span class="item__expiry">${i18n("dashboard.card.deleted.in")?replace("{0}", prettytime(item.deleteAt))}</span>
                        <#else>
                            <span>${prettytime(item.sort)}</span>
                        </#if>
                    </div>
                </div>
            </article>
        </#list>
    </div>

    <#assign moveTargets = 0>
    <#list categories as category>
        <#if category.name?lower_case != "trash" && category.uid != categoryUid>
            <#assign moveTargets = moveTargets + 1>
        </#if>
    </#list>

    <div class="tray" id="tray">
        <span class="tray__n" id="tray-n"></span>
        <#if !isTrash && moveTargets gt 0>
            <button class="btn btn--quiet btn--sm" id="bulk-move" type="button">
                <@icons.icon "folder"/>${i18n("dashboard.bulk.move")}
            </button>
        </#if>
        <button class="btn btn--quiet btn--sm" id="bulk-copy" type="button">
            <@icons.icon "link"/>${i18n("dashboard.bulk.copy")}
        </button>
        <#if !isTrash>
            <button class="btn btn--quiet btn--sm" id="bulk-delete" type="button">
                <@icons.icon "trash"/>${i18n("dashboard.bulk.delete")}
            </button>
        </#if>
        <span class="tray__div"></span>
        <button class="btn btn--quiet btn--sm" id="pick-done" type="button">
            ${i18n("dashboard.select.done")}<kbd style="margin-left:6px">esc</kbd>
        </button>
    </div>
<#else>
    <div class="blank">
        <div class="blank__mark">¶</div>
        <div class="blank__title"><#if isTrash>${i18n("dashboard.category.trash.title")}<#else>${i18n("dashboard.category.title")}</#if></div>
        <p class="blank__text"><#if isTrash>${i18n("dashboard.category.trash.subtitle")}<#else>${i18n("dashboard.category.subtitle")}</#if></p>
        <#if !isTrash>
            <div class="actions" style="justify-content:center;margin-top:18px">
                <button class="btn btn--ink" id="add-bookmark-empty" type="button">
                    <@icons.icon "plus"/>${i18n("layout.add.bookmark")}
                </button>
                <a class="btn btn--line" href="/dashboard/io">
                    <@icons.icon "upload"/>${i18n("io.import.title")}
                </a>
            </div>
        </#if>
    </div>
</#if>
</@layout.myLayout>
