<#import "../layout.ftl" as layout>
<#import "../_icons.ftl" as icons>
<@layout.myLayout "About">
<div class="column">
    <div class="page-head" style="margin-bottom:26px">
        <div>
            <h1 class="page-head__title">Filed Papers</h1>
            <div class="page-head__meta">
                <span class="tag tag--accent">${version}</span>
            </div>
        </div>
    </div>

    <p style="font-family:var(--serif);font-size:18px;line-height:1.6;letter-spacing:-.005em">
        Filed Papers is a self-hosted bookmark manager for people who would rather keep their
        reading list on their own server than on somebody else's. Save a page from the web
        interface, the iOS app or the Chrome extension, and it stays where you put it.
    </p>

    <div class="sheet">
        <div class="sheet__body" style="margin-top:26px">
            <div class="line line--stack">
                <div class="line__label">How it works</div>
                <div class="line__hint" style="max-width:none">
                    <ul style="margin:8px 0 0;padding-left:18px;display:flex;flex-direction:column;gap:5px">
                        <li>Host the backend yourself and keep full control over your bookmarks.</li>
                        <li>Save bookmarks from the web interface, the iOS app or the Chrome extension.</li>
                        <li>Organise them into categories and reach them from any device.</li>
                    </ul>
                </div>
            </div>
            <div class="line">
                <div>
                    <div class="line__label">Source code</div>
                    <div class="line__hint">github.com/svenkubiak/filedpapers</div>
                </div>
                <a class="btn btn--line line__ctrl" href="https://github.com/svenkubiak/filedpapers" target="_blank" rel="noopener">
                    <@icons.icon "github"/>GitHub
                </a>
            </div>
            <div class="line" style="border-bottom:none">
                <div>
                    <div class="line__label">Support the project</div>
                    <div class="line__hint">Filed Papers is free and stays that way.</div>
                </div>
                <a class="btn btn--tint line__ctrl" href="https://www.buymeacoffee.com/svenkubiak" target="_blank" rel="noopener">
                    <@icons.icon "heart"/>Buy me a coffee
                </a>
            </div>
        </div>
    </div>
</div>
</@layout.myLayout>
