<#import "./layout.ftl" as layout>
<@layout.myLayout "Layout">
    <div class="auth-container">
        <div class="auth-box">
            <span class="icon auth-logo">
                <i class="fas fa-bookmark fa-8x"></i>
            </span>
            <h1 class="auth-title">Welcome to Filed Papers</h1>
            <p class="auth-subtitle">Please login to proceed.</p>

            <#if flash.signup?? && flash.signup == "success">

            <div class="notification is-success is-light mb-5">
                <button class="delete"></button>
                Your account has been created!
            </div>

            </#if>

            <#if flash.login?? && flash.login == "error">

                <div class="notification is-danger is-light mb-5">
                    <button class="delete"></button>
                    Invalid Username/Password!
                </div>

            </#if>

            <form action="/auth/login" method="POST" onsubmit="showLoading()">
                <div class="field">
                    <div class="control has-icons-left<#if form.hasError("username")> has-icons-right</#if>">
                        <input class="input<#if form.hasError("username")> is-danger</#if>" type="email" placeholder="Your Email" id="username" value="<#if form.username??>${form.username}</#if>" name="username" required>
                        <span class="icon is-small is-left">
                            <i class="fas fa-envelope"></i>
                        </span>
                        <#if form.hasError("username")>
                        <span class="icon is-small is-right">
                            <i class="fas fa-exclamation-triangle"></i>
                        </span>
                        </#if>
                    </div>
                    <#if form.hasError("username")>
                        <p class="help is-danger">${form.getError("username")}</p>
                    </#if>
                </div>

                <div class="field">
                    <div class="control has-icons-left<#if form.hasError("password")> has-icons-right</#if>">
                        <input class="input<#if form.hasError("password")> is-danger</#if>" type="password" placeholder="Your Password" name="password" required>
                        <span class="icon is-small is-left">
                            <i class="fas fa-lock"></i>
                        </span>
                        <#if form.hasError("password")>
                        <span class="icon is-small is-right">
                            <i class="fas fa-exclamation-triangle"></i>
                        </span>
                        </#if>
                    </div>
                    <#if form.hasError("password")>
                        <p class="help is-danger">${form.getError("password")}</p>
                    </#if>
                </div>

                <div class="field">
                    <div class="control">
                        <label class="checkbox">
                            <input type="checkbox" value="1" name="rememberme">
                            Remember me
                        </label>
                    </div>
                </div>

                <div class="field">
                    <div class="control">
                        <button type="submit" class="button is-link is-fullwidth" id="loading-button">
                            Login
                        </button>
                    </div>
                </div>
            </form>

            <div class="auth-links">
                <#if registration>
                <a href="/auth/signup">Sign Up</a>
                <span>·</span>
                </#if>
                <a href="/auth/forgot">Forgot Password?</a>
            </div>
        </div>
    </div>
</@layout.myLayout>