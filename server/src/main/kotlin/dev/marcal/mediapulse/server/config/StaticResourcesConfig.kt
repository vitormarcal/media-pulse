package dev.marcal.mediapulse.server.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.io.File
import java.util.concurrent.TimeUnit

@Configuration
class StaticResourcesConfig(
    @Value("\${media-pulse.storage.covers-path}") private val coversPath: String,
    frontendProperties: FrontendProperties,
) : WebMvcConfigurer {
    private val frontendStaticRootLocations =
        buildList {
            val staticPath = frontendProperties.staticPath.trim()
            if (staticPath.isNotEmpty()) {
                add("file:${ensureTrailingSlash(File(staticPath).absolutePath)}")
            }
            add("classpath:/static/")
        }.toTypedArray()

    private val frontendNuxtLocations =
        frontendStaticRootLocations.map { "${it}_nuxt/" }.toTypedArray()

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/covers/**")
            .addResourceLocations("file:$coversPath")

        registry
            .addResourceHandler("/_nuxt/**")
            .addResourceLocations(*frontendNuxtLocations)
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())

        registry
            .addResourceHandler("/_nuxt/builds/**")
            .addResourceLocations(
                *frontendNuxtLocations.map { "${it}builds/" }.toTypedArray(),
            ).setCacheControl(CacheControl.noCache().mustRevalidate().cachePrivate())

        registry
            .addResourceHandler("/*.html")
            .addResourceLocations(*frontendStaticRootLocations)
            .setCacheControl(CacheControl.noCache().mustRevalidate().cachePrivate())

        registry
            .addResourceHandler("/favicon.ico", "/robots.txt")
            .addResourceLocations(*frontendStaticRootLocations)
            .setCacheControl(CacheControl.noCache().mustRevalidate().cachePrivate())
    }

    private fun ensureTrailingSlash(path: String): String = if (path.endsWith("/")) path else "$path/"
}
