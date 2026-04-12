package dev.marcal.mediapulse.server.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.concurrent.TimeUnit

@Configuration
class StaticResourcesConfig(
    @Value("\${media-pulse.storage.covers-path}") private val coversPath: String,
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/covers/**")
            .addResourceLocations("file:$coversPath")

        registry
            .addResourceHandler("/_nuxt/**", "/*.js", "/*.css", "/favicon.ico", "/robots.txt")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())

        registry
            .addResourceHandler("/_nuxt/builds/**")
            .addResourceLocations("classpath:/static/_nuxt/builds/")
            .setCacheControl(CacheControl.noCache().mustRevalidate().cachePrivate())

        registry
            .addResourceHandler("/*.html")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.noCache().mustRevalidate().cachePrivate())
    }
}
