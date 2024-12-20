<#import "../layout.ftl" as layout>
<@layout.myLayout "Layout">
<!-- Breadcrumb -->
<nav class="breadcrumb has-arrow-separator mb-5" aria-label="breadcrumbs">
    <ul>
        <li class="is-active">
            <a href="#" aria-current="page">
                        <span class="icon is-small">
                            <i class="fas fa-user-cog"></i>
                        </span>
                <span>Profile</span>
            </a>
        </li>
    </ul>
</nav>

<!-- Profile Form -->
<div class="profile-form">
    <!-- Change Email Form -->
    <form class="profile-section">
        <h2 class="section-title">Change Email</h2>
        <div class="form-field">
            <label class="label">Current Email</label>
            <div class="control has-icons-left">
                <input class="input" type="email" value="current@email.com" disabled>
                <span class="icon is-small is-left">
                            <i class="fas fa-envelope"></i>
                        </span>
            </div>
        </div>

        <div class="form-field">
            <label class="label">New Email</label>
            <div class="control has-icons-left">
                <input class="input" type="email" placeholder="new@email.com">
                <span class="icon is-small is-left">
                            <i class="fas fa-envelope"></i>
                        </span>
            </div>
        </div>

        <div class="form-field">
            <label class="label">Password</label>
            <div class="control has-icons-left">
                <input class="input" type="password" placeholder="Confirm with your password">
                <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
            </div>
        </div>

        <div class="form-field">
            <div class="control">
                <button class="button is-link is-fullwidth">
                    Update Email
                </button>
            </div>
        </div>
    </form>

    <!-- Change Password Form -->
    <form class="profile-section">
        <h2 class="section-title">Change Password</h2>
        <div class="form-field">
            <label class="label">Current password</label>
            <div class="control has-icons-left">
                <input class="input" type="password" placeholder="Current password">
                <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
            </div>
        </div>

        <div class="form-field">
            <label class="label">New password</label>
            <div class="control has-icons-left">
                <input class="input" type="password" placeholder="New password">
                <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
            </div>
        </div>

        <div class="form-field">
            <label class="label">Confirm new password</label>
            <div class="control has-icons-left">
                <input class="input" type="password" placeholder="Confirm new password">
                <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
            </div>
        </div>

        <div class="form-field">
            <div class="control">
                <button class="button is-link is-fullwidth">
                    Update Password
                </button>
            </div>
        </div>
    </form>
</div>
</@layout.myLayout>