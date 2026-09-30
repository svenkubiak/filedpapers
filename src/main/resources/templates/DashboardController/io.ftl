<#import "../layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "Import & Export">
<div class="column">
    <div class="page-head" style="margin-bottom:26px">
        <div>
            <h1 class="page-head__title">${i18n("io.breadcrumbs")}</h1>
            <div class="page-head__meta"><span>${i18n("io.subtitle")}</span></div>
        </div>
    </div>

    <div class="sheet">
        <div class="sheet__title">${i18n("io.import.title")}</div>
        <form action="/dashboard/io/importer" method="post" enctype="multipart/form-data" data-busy="import-button">
            <div class="sheet__body" style="border-top:none;padding-top:6px">
                <label class="drop" for="importfile">
                    <@icons.icon "upload"/>
                    <span style="font-size:15px;font-family:var(--serif)" id="importfile-name">${i18n("io.import.drop")}</span>
                    <span class="hint">${i18n("io.import.formats")}</span>
                    <input class="is-hidden" type="file" name="importfile" id="importfile" accept=".html,.htm,.json">
                </label>

                <div class="note note--accent" style="margin-top:16px">
                    <@icons.icon "info"/>
                    <div>
                        <div class="note__title">${i18n("io.import.note.title")}</div>
                        <div class="note__text">${i18n("io.import.note.text")}</div>
                    </div>
                </div>

                <div class="actions" style="justify-content:flex-end;margin-top:18px">
                    <button type="submit" class="btn btn--ink" id="import-button">${i18n("io.import.button")}</button>
                </div>
            </div>
            <@csrfform/>
        </form>
    </div>

    <div class="sheet">
        <div class="sheet__title">${i18n("io.export.title")}</div>
        <form action="/dashboard/io/exporter" method="post">
            <div class="sheet__body">
                <div class="line" style="border-bottom:none">
                    <div>
                        <div class="line__label">${i18n("io.export.hint")}</div>
                    </div>
                    <button type="submit" class="btn btn--line line__ctrl">
                        <@icons.icon "download"/>${i18n("io.export.button")}
                    </button>
                </div>
            </div>
            <@csrfform/>
        </form>
    </div>
</div>
</@layout.myLayout>
