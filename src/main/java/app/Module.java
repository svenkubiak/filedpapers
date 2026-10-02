package app;

import com.google.inject.AbstractModule;
import io.mangoo.interfaces.MangooBootstrap;
import io.mangoo.interfaces.TokenBlacklist;
import jakarta.inject.Singleton;
import services.PersistentTokenBlacklist;

@Singleton
public class Module extends AbstractModule {
    @Override
    protected void configure() {
        bind(MangooBootstrap.class).to(Bootstrap.class);
        bind(TokenBlacklist.class).to(PersistentTokenBlacklist.class);
    }
}