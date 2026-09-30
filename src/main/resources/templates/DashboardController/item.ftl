<#import "../_item.ftl" as tile>
<#-- A single tile, without the surrounding page: the dashboard asks for this
     when an event says a bookmark arrived, and inserts it at the top. -->
<@tile.tile item categoryUid isTrash/>
