<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
    <div class="auth-container">
        <div class="auth-box">
        <span class="icon auth-logo" style="color: green;">
            <i class="fas fa-bookmark fa-8x"></i>
        </span>
            <h1 class="auth-title">Success</h1>
            <p class="auth-subtitle">Your request has been processed successfully. Everything is set!</p>
            <div class="auth-links">
                <a href="/auth/login">Back to Login</a>
            </div>
        </div>
    </div>
</@layout.myLayout>