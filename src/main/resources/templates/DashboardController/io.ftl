<#import "../layout.ftl" as layout>
<@layout.myLayout "Layout">
<nav class="breadcrumb" aria-label="breadcrumbs">
    <ul>
        <li class="is-active">
            <a href="/dashboard/io" aria-current="page"><span class="icon is-small">
                <i class="fas fa-sync"></i></span><span>${i18n("io.breadcrumbs")}</span>
            </a>
        </li>
    </ul>
</nav>
<div class="columns is-multiline">
    <div class="column is-half">
        <div class="profile-form">
            <form action="/dashboard/io/importer" method="post" enctype="multipart/form-data" class="profile-section">
                <h2 class="section-title">${i18n("io.import.title")}</h2>
                <div class="form-field">
                    <label class="label">${i18n("io.import.label")}</label>
                    <div class="control has-icons-left">
                        <input class="input" type="file" name="importfile">
                        <span class="icon is-small is-left"><i class="fas fa-file"></i></span>
                    </div>
                </div>
                <div class="form-field">
                    <div class="control">
                        <input type="submit" class="button is-link is-fullwidth" value="${i18n("io.import.button")}">
                    </div>
                </div>
            </form>
            <form action="/dashboard/io/exporter" method="post" class="profile-section">
                <h2 class="section-title">${i18n("io.export.title")}</h2>
                <div class="form-field">
                    <div class="control">
                        <input type="submit" class="button is-link is-fullwidth" value="${i18n("io.export.button")}">
                    </div>
                </div>
            </form>
        </div>
    </div>
</div>
</@layout.myLayout>