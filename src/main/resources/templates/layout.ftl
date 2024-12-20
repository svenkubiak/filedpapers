<#macro myLayout title="Layout example">
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Filed Papers</title>
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.2/css/bulma.min.css">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css">
        <link rel="stylesheet" href="/assets/css/app.css">
    </head>
    <body>
    <aside class="sidebar">
        <div class="sidebar-content">
            <p class="menu-label menu-label-with-icon">
                Bookmarks
                <span class="icon is-small" style="cursor: pointer;" id="add-bookmark-button">
                    <i class="fas fa-plus"></i>
                </span>
            </p>
            <ul class="menu-list">
                <li>
                    <a data-category="inbox" class="is-active">
                        <span class="icon">
                            <i class="fas fa-inbox"></i>
                        </span>
                        Inbox
                        <span class="tag is-rounded is-pulled-right">4</span>
                    </a>
                </li>
                <li>
                    <a data-category="archive">
                        <span class="icon">
                            <i class="fas fa-archive"></i>
                        </span>
                        Archive
                        <span class="tag is-rounded is-pulled-right">7</span>
                    </a>
                </li>
                <li>
                    <a data-category="trash">
                        <span class="icon">
                            <i class="fas fa-trash"></i>
                        </span>
                        Trash
                        <span class="tag is-rounded is-pulled-right">3</span>
                    </a>
                </li>
            </ul>
        </div>

        <div class="sidebar-footer">
            <div class="buttons">
                <a href="/dashboard/profile" class="button is-light is-fullwidth mb-2">
                    <span class="icon">
                        <i class="fas fa-user-cog"></i>
                    </span>
                    <span>Profile</span>
                </a>
                <a href="/auth/logout" class="button is-danger is-light is-fullwidth">
                    <span class="icon">
                        <i class="fas fa-sign-out-alt"></i>
                    </span>
                    <span>Logout</span>
                </a>
            </div>
        </div>
    </aside>

    <!-- Add Bookmark Modal -->
    <div class="modal" id="add-bookmark-modal">
        <div class="modal-background"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title">Add Bookmark</p>
                <button class="delete" aria-label="close"></button>
            </header>
            <section class="modal-card-body">
                <div class="field">
                    <label class="label">URL</label>
                    <div class="control">
                        <input class="input" type="url" placeholder="https://example.com">
                    </div>
                </div>
                <div class="field">
                    <label class="label">Category</label>
                    <div class="control">
                        <div class="select is-fullwidth">
                            <select>
                                <option>Inbox</option>
                                <option>Archive</option>
                            </select>
                        </div>
                    </div>
                </div>
            </section>
            <footer class="modal-card-foot">
                <button class="button is-primary">Add</button>
                <button class="button">Cancel</button>
            </footer>
        </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <div class="modal" id="delete-confirm-modal">
        <div class="modal-background"></div>
        <div class="modal-card">
            <header class="modal-card-head">
                <p class="modal-card-title">Delete Bookmark</p>
                <button class="delete" aria-label="close"></button>
            </header>
            <section class="modal-card-body">
                <p>Are you sure you want to delete this bookmark?</p>
            </section>
            <footer class="modal-card-foot">
                <button class="button is-danger" id="confirm-delete">Delete</button>
                <button class="button">Cancel</button>
            </footer>
        </div>
    </div>

    <!-- Main Content -->
    <div class="main-content">
       <#nested/>
    </div>

    <script src="/assets/js/app.js"></script>
    </body>
    </html>
</#macro>
