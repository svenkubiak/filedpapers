<#import "../layout.ftl" as layout>
<@layout.myLayout "Layout">
<nav class="breadcrumb" aria-label="breadcrumbs">
    <ul>
        <li class="is-active">
            <a href="/dashboard/io" aria-current="page"><span class="icon is-small">
                <i class="fas fa-info-circle"></i></span><span>${i18n("about.breadcrumbs")}</span>
            </a>
        </li>
    </ul>
</nav>
<p class="content">
    Filed Papers is a <strong>self-hosted bookmark manager</strong> designed for users who value privacy and control over their data. Unlike traditional bookmarking services, Filed Papers ensures that your saved links are stored on your own server, keeping them secure and accessible only to you.
</p>

<h2 class="title is-4">How It Works</h2>
<ul class="content">
    <li><strong>Host the backend</strong> yourself to maintain full control over your bookmarks.</li>
    <li><strong>Save bookmarks</strong> easily using the web interface, iOS app, or Chrome extension.</li>
    <li><strong>Organize and access</strong> your saved links anytime, from any device.</li>
</ul>

<p class="content">
    Filed Papers provides a <strong>clean, intuitive interface</strong> that makes managing your bookmarks simple and efficient. Whether you're organizing research, saving articles, or keeping track of important resources, this app helps you stay in control.
</p>

<div class="mt-5">
    <p class="subtitle is-6">Version: <strong>${version}</strong></p>
</div>

<div class="mt-4">
    <a href="https://www.buymeacoffee.com/svenkubiak" target="_blank" class="button is-warning is-medium">
        Buy Me a Coffee ☕
    </a>
    <a href="https://github.com/svenkubiak/filedpapers" target="_blank" class="button is-light is-medium ml-3">
    <span class="icon"><i class="fab fa-github"></i></span><span>GitHub</span></a>
</div>
</@layout.myLayout>