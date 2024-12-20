package app;

import com.google.inject.AbstractModule;
import io.mangoo.interfaces.MangooBootstrap;
import jakarta.inject.Singleton;

@Singleton
public class Module extends AbstractModule {
    @Override
    protected void configure() {
        bind(MangooBootstrap.class).to(Bootstrap.class);
    }
}