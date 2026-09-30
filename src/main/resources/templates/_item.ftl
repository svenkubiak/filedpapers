<#--
  One bookmark tile.

  Used by the dashboard for the whole list, and by the item fragment endpoint
  for a single tile that arrived while the page was open. Keeping it in one
  place is the reason the dashboard can insert a new bookmark without
  rebuilding the markup in javascript - the server renders the same thing both
  times.
-->
<#macro tile item categoryUid isTrash=false>
<#import "_icons.ftl" as icons>
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
    <#-- Deliberately a sibling of the frame, not a child: in the compact view
         these sit at the end of the row, and inside the frame they would be
         clipped by its overflow and painted over by the link. -->
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
</#macro>
