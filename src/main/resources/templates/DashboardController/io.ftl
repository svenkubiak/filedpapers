<#import "../layout.ftl" as layout>
<@layout.myLayout "Layout">
<!-- Breadcrumb -->
<nav class="breadcrumb" aria-label="breadcrumbs">
    <ul>
        <li class="is-active">
            <a href="/dashboard/io" aria-current="page"><span class="icon is-small"><i class="fas fa-sync"></i></span><span>Import/Export</span></a>
        </li>
    </ul>
</nav>

    <div class="columns is-multiline">
        <div class="column is-half">


        <div class="profile-form">
    <!-- Change Email Form -->
    <form action="/dashboard/importer" method="post" enctype="multipart/form-data" class="profile-section">
        <h2 class="section-title">Import</h2>
        <div class="form-field">
            <label class="label">File to import</label>
            <div class="control has-icons-left">
                <input class="input" type="file" name="importfile">
                <span class="icon is-small is-left">
                            <i class="fas fa-file"></i>
                        </span>
            </div>
        </div>

        <div class="form-field">
            <div class="control">
                <input type="submit">
            </div>
        </div>
    </form>

    <!-- Change Password Form -->
    <form action="/dashboard/exporter" method="post" class="profile-section">
        <h2 class="section-title">Export</h2>
        <div class="form-field">
            <label class="label">File to export</label>
            <div class="control has-icons-left">
                <input class="input" type="text" placeholder="Filename">
                <span class="icon is-small is-left">
                            <i class="fas fa-file"></i>
                        </span>
            </div>
        </div>

          <div class="form-field">
            <div class="control">
                <button class="button is-link is-fullwidth">
                    Start export
                </button>
            </div>
        </div>
    </form>
</div>

        </div>
    </div>
</@layout.myLayout>