<#import "../layout.ftl" as layout>
<@layout.myLayout "Layout">
    <!-- Breadcrumb -->
    <nav class="breadcrumb has-arrow-separator mb-5" aria-label="breadcrumbs">
        <ul>
            <li>
                <a href="#">
                        <span class="icon is-small">
                            <i class="fas fa-bookmark"></i>
                        </span>
                    <span>Bookmarks</span>
                </a>
            </li>
            <li class="is-active"><a href="#" aria-current="page">Inbox</a></li>
        </ul>
    </nav>

    <div class="columns is-multiline">
        <!-- Example of how each card should look -->
        <div class="column is-one-quarter">
            <div class="card">
                <a href="https://youtube.com" target="_blank" class="card-link">
                    <div class="card-image">
                        <img src="https://placehold.co/640x360" alt="Thumbnail">
                    </div>
                    <div class="card-content">
                        <div class="card-title-wrapper">
                            <p class="card-title" draggable="true" data-id="1">Best-Of Dashcam 2024 - Unfälle, Road-Rage, heftige Verfolgungsjagden</p>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                        <div class="card-meta">
                            <span class="card-domain">youtube.com</span>
                            <span class="card-added">Added 2h ago</span>
                        </div>
                    </div>
                </a>
            </div>
        </div>

        <!-- Update all other cards to match this structure -->
        <div class="column is-one-quarter">
            <div class="card">
                <a href="https://bulmatemplates.github.io" target="_blank" class="card-link">
                    <div class="card-image">
                        <img src="https://placehold.co/640x360" alt="Thumbnail">
                    </div>
                    <div class="card-content">
                        <div class="card-title-wrapper">
                            <p class="card-title" draggable="true" data-id="2">Free Bulma templates</p>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                        <div class="card-meta">
                            <span class="card-domain">bulmatemplates.github.io</span>
                            <span class="card-added">Added 19 Dec</span>
                        </div>
                    </div>
                </a>
            </div>
        </div>

        <!-- Third Card Example -->
        <div class="column is-one-quarter">
            <div class="card">
                <a href="https://heise.de" target="_blank" class="card-link">
                    <div class="card-image">
                        <img src="https://placehold.co/640x360" alt="Thumbnail">
                    </div>
                    <div class="card-content">
                        <div class="card-title-wrapper">
                            <p class="card-title" draggable="true" data-id="3">Chrome 131.0.6778.254/255 stopft Sicherheitslücken</p>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                        <div class="card-meta">
                            <span class="card-domain">heise.de</span>
                            <span class="card-added">Added 18 Dec</span>
                        </div>
                    </div>
                </a>
            </div>
        </div>

        <!-- Additional Cards -->
        <div class="column is-one-quarter">
            <div class="card" draggable="true" data-id="4">
                <div class="card-image">
                    <img src="https://placehold.co/640x360" alt="Thumbnail">
                </div>
                <div class="card-content">
                    <p class="card-title">Life-Changing Git Wrapping Hacks</p>
                    <div class="card-meta">
                        <span class="card-domain">github.com</span>
                        <div class="card-date">
                            <span>Added 18 Dec</span>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="column is-one-quarter">
            <div class="card" draggable="true" data-id="5">
                <div class="card-image">
                    <img src="https://placehold.co/640x360" alt="Thumbnail">
                </div>
                <div class="card-content">
                    <p class="card-title">How to Submit Your App to the App Store in 2024</p>
                    <div class="card-meta">
                        <span class="card-domain">developer.apple.com</span>
                        <div class="card-date">
                            <span>Added 17 Dec</span>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="column is-one-quarter">
            <div class="card" draggable="true" data-id="6">
                <div class="card-image">
                    <img src="https://placehold.co/640x360" alt="Thumbnail">
                </div>
                <div class="card-content">
                    <p class="card-title">I Made an iOS App in MINUTES with This AI Tool!</p>
                    <div class="card-meta">
                        <span class="card-domain">youtube.com</span>
                        <div class="card-date">
                            <span>Added 16 Dec</span>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="column is-one-quarter">
            <div class="card" draggable="true" data-id="7">
                <div class="card-image">
                    <img src="https://placehold.co/640x360" alt="Thumbnail">
                </div>
                <div class="card-content">
                    <p class="card-title">Wind River (2017) ⭐ 7.7 | Krimi, Drama, Mystery</p>
                    <div class="card-meta">
                        <span class="card-domain">imdb.com</span>
                        <div class="card-date">
                            <span>Added 15 Dec</span>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="column is-one-quarter">
            <div class="card" draggable="true" data-id="8">
                <div class="card-image">
                    <img src="https://placehold.co/640x360" alt="Thumbnail">
                </div>
                <div class="card-content">
                    <p class="card-title">Christian Stöcker (gesellschaftlicher-zusammenhalt.social)</p>
                    <div class="card-meta">
                        <span class="card-domain">mastodon.social</span>
                        <div class="card-date">
                            <span>Added 15 Dec</span>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="column is-one-quarter">
            <div class="card" draggable="true" data-id="9">
                <div class="card-image">
                    <img src="https://placehold.co/640x360" alt="Thumbnail">
                </div>
                <div class="card-content">
                    <p class="card-title">Lauter Kühlschranktür im Westfalia Columbus</p>
                    <div class="card-meta">
                        <span class="card-domain">camperboard.de</span>
                        <div class="card-date">
                            <span>Added 14 Dec</span>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="column is-one-quarter">
            <div class="card" draggable="true" data-id="10">
                <div class="card-image">
                    <img src="https://placehold.co/640x360" alt="Thumbnail">
                </div>
                <div class="card-content">
                    <p class="card-title">Weihnachskekse perfekt machen: Diese Fehler sind verbreitet</p>
                    <div class="card-meta">
                        <span class="card-domain">kurier.at</span>
                        <div class="card-date">
                            <span>Added 14 Dec</span>
                            <span class="icon card-trash">
                                    <i class="fas fa-trash-alt"></i>
                                </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</@layout.myLayout>
