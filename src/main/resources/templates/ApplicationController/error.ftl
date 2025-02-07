<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
    <div class="auth-container">
        <div class="auth-box">
        <span class="icon auth-logo" style="color: red;">
            <i class="fas fa-bookmark fa-8x"></i>
        </span>
            <h1 class="auth-title">Error</h1>
            <p class="auth-subtitle">The requested URL does not exist or expired.</p>
            <div class="auth-links">
                <a href="/auth/login">Back to Login</a>
            </div>
        </div>
    </div>
</@layout.myLayout>