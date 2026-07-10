@KotlinBuildScript("https://tools.kotlin.build/")
@file:WithArtifact("kompile:build-kotlin-jvm:0.0.23")
package screenshottest.wui

import build.kotlin.withartifact.WithArtifact
import java.io.File
import build.kotlin.jvm.*
import build.kotlin.annotations.MavenArtifactCoordinates

val dependencies = resolveDependencies2(
    // Jetty server
    MavenPrebuilt2("org.eclipse.jetty:jetty-server:11.0.20"),
    MavenPrebuilt2("org.eclipse.jetty:jetty-servlet:11.0.20"),
    MavenPrebuilt2("org.eclipse.jetty:jetty-http:11.0.20"),
    MavenPrebuilt2("org.eclipse.jetty:jetty-io:11.0.20"),
    MavenPrebuilt2("org.eclipse.jetty:jetty-util:11.0.20"),
    MavenPrebuilt2("org.eclipse.jetty:jetty-security:11.0.20"),
    // Jakarta Servlet API
    MavenPrebuilt2("jakarta.servlet:jakarta.servlet-api:5.0.0"),
    // UrlResolver and UrlProtocol — the WUI connects to url://screenshottest/ as a typed proxy.
    // 0.0.556 / 0.0.303: matched pair. protocol 0.0.303 = inbound mux-substream OOM fixes;
    //          resolver 0.0.556 is compiled against protocol 0.0.303 and is binary-compatible
    //          with it. They MUST be bumped together. Kept in lockstep with BuildTestWui.
    MavenPrebuilt2("foundation.url:resolver:0.0.600"),
    MavenPrebuilt2("foundation.url:protocol:0.0.303"),
    // SJVM for sandboxed execution (required by UrlResolver.openSandboxedConnection)
    MavenPrebuilt2("net.javadeploy.sjvm:libSJVM-jvm:0.0.38"),
    MavenPrebuilt2("net.javadeploy.sjvm:avianStdlibHelper-jvm:0.0.38"),
    MavenPrebuilt2("net.javadeploy.sjvm:stdlibHelperCommon-jvm:0.0.38"),
    // ASM for bytecode manipulation (required by UrlResolver sandbox proxy generation)
    MavenPrebuilt2("org.ow2.asm:asm:9.8"),
    MavenPrebuilt2("org.ow2.asm:asm-commons:9.8"),
    MavenPrebuilt2("org.ow2.asm:asm-util:9.8"),
    MavenPrebuilt2("org.ow2.asm:asm-tree:9.8"),
    // Clock abstraction (required by UrlProtocol and by the WUI's createServer seam)
    MavenPrebuilt2("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.3"),
    // RPC protocol
    MavenPrebuilt2("community.kotlin.rpc:protocol-api:0.0.2"),
    MavenPrebuilt2("community.kotlin.rpc:protocol-impl:0.0.11"),
    // ScreenshotTest API — the url://screenshottest/ contract this WUI renders.
    MavenPrebuilt2("screenshottest.api:screenshottest-api:0.0.1"),
    // JSON
    MavenPrebuilt2("org.json:json:20250517"),
    // Okio
    MavenPrebuilt2("com.squareup.okio:okio-jvm:3.4.0"),
    // Kotlin stdlib
    MavenPrebuilt2("org.jetbrains.kotlin:kotlin-stdlib:1.9.22"),
    MavenPrebuilt2("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22"),
    MavenPrebuilt2("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22"),
    MavenPrebuilt2("org.jetbrains.kotlin:kotlin-reflect:1.9.22"),
    // Coroutines
    MavenPrebuilt2("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0"),
    // Logging
    MavenPrebuilt2("org.slf4j:slf4j-api:1.7.36"),
    MavenPrebuilt2("org.slf4j:slf4j-simple:1.7.36"),
    // libp2p
    MavenPrebuilt2("community.kotlin.libp2p:jvm-libp2p:1.3.0-codexcoder21-snapshot-4"),
    MavenPrebuilt2("com.google.protobuf:protobuf-java:3.25.1"),
    MavenPrebuilt2("tech.pegasys:noise-java:22.1.0"),
    // Netty (for libp2p)
    MavenPrebuilt2("io.netty:netty-buffer:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-codec:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-codec-http:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-codec-http2:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-common:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-handler:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-resolver:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-transport:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-transport-classes-epoll:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-transport-classes-kqueue:4.2.10.Final"),
    MavenPrebuilt2("io.netty:netty-transport-native-unix-common:4.2.10.Final"),
    // BouncyCastle
    MavenPrebuilt2("org.bouncycastle:bcpkix-jdk18on:1.78.1"),
    MavenPrebuilt2("org.bouncycastle:bcprov-jdk18on:1.78.1"),
    MavenPrebuilt2("org.bouncycastle:bcutil-jdk18on:1.78.1"),
    // Guava
    MavenPrebuilt2("com.google.guava:guava:33.2.0-jre"),
    MavenPrebuilt2("com.google.guava:failureaccess:1.0.2"),
    // UrlResolver transitive dependencies
    MavenPrebuilt2("community.kotlin.observable:core-jvm:0.2.3"),
    MavenPrebuilt2("util.stacktrace:util-stacktrace:0.0.1"),
    MavenPrebuilt2("kompile.effects:kompile-effects:0.0.1"),
    MavenPrebuilt2("community.kotlin.logging:community-kotlin-logging:0.0.1"),
    MavenPrebuilt2("util.cache.hashtofile:hash-to-file-cache:0.0.4"),
    MavenPrebuilt2("cache.key.builder:cache-key-builder:0.0.3"),
    MavenPrebuilt2("file.utils:file-utils:0.0.1"),
    MavenPrebuilt2("util.string:stringutils:0.0.1"),
    MavenPrebuilt2("resource.arena:resource-arena:0.0.1"),
    MavenPrebuilt2("kotlinc.diagnostic.collector:kotlinc-diagnostic-collector:0.0.1"),
    MavenPrebuilt2("kompile.buildrule.enhancer:kompile-buildrule-enhancer:0.0.1"),
    MavenPrebuilt2("refcounted:refcounted:0.0.1"),
    MavenPrebuilt2("util.jar:jarutils:0.0.1"),
    MavenPrebuilt2("kompile.interfaces.internal:kompile-interfaces-internal:0.0.1"),
    MavenPrebuilt2("tools.kotlin.build.compile.interfaces.executionenvironment:tools-kotlin-build-compile-interfaces-executionenvironment:0.0.1"),
    MavenPrebuilt2("tools.kotlin.build.compile.interfaces.diagnostics:tools-kotlin-build-compile-interfaces-diagnostics:0.0.1"),
    MavenPrebuilt2("community.kotlin.coursier.bldbinary:community-kotlin-coursier-bldbinary:0.0.1"),
    MavenPrebuilt2("io.get-coursier:interface:0.0.0+481-d9800bd9-SNAPSHOT"),
    MavenPrebuilt2("io.get-coursier:coursier-util_2.13:2.1.30"),
    MavenPrebuilt2("io.get-coursier:coursier-core_2.13:2.1.30"),
    MavenPrebuilt2("io.get-coursier:coursier_2.13:2.1.30"),
    MavenPrebuilt2("io.get-coursier:coursier-cache_2.13:2.1.30"),
    MavenPrebuilt2("algebraic.effects.v1.runtime:algebraic-effects-v1-runtime:0.0.1"),
    // W3Wallet API (transitive dependency of UrlResolver SJVM sandbox setup)
    MavenPrebuilt2("w3wallet.api:w3wallet-api:0.0.1"),
)

@MavenArtifactCoordinates("screenshottest.wui:screenshottest-wui:")
fun buildMaven(): File {
    return buildSimpleKotlinMavenArtifact(
        // 0.0.1: Initial release.
        //        - Minimal session gallery / diff viewer for url://screenshottest/:
        //          "/" sessions list, "/session?id=" detail with the per-key verdict
        //          table and inline actual/golden/diff thumbnails, "/image?id=&key=&kind="
        //          chunk-streamed PNG endpoint (bounded heap for the -Xmx128m WUI),
        //          and "/health".
        //        - createServer(port, api, clock) hermetic seam (community.kotlin.clocks.simple);
        //          relative "createdAt" times are anchored to the injected clock so pages are
        //          time-stable for golden-screenshot capture.
        //        - Dogfoods the ScreenshotTest service via ScreenshotFixtureServer +
        //          tests/goldenScreenshots.kts (its own committed goldens under screenshots/).
        coordinates = "screenshottest.wui:screenshottest-wui:0.0.1",
        src = File("src"),
        compileDependencies = dependencies
    )
}

fun buildSkinnyJar(): File {
    return buildMaven().jar
}

fun buildFatJar(): File {
    val manifest = Manifest("screenshottest.wui.MainKt")
    return BuildJar(manifest, dependencies.map { it.jar } + buildSkinnyJar())
}
