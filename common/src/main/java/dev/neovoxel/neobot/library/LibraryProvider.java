package dev.neovoxel.neobot.library;

import dev.neovoxel.jarflow.JarFlow;
import dev.neovoxel.jarflow.dependency.Dependency;
import dev.neovoxel.jarflow.loader.ExternalLoader;
import dev.neovoxel.jarflow.remote.DependencyDownloader;
import dev.neovoxel.jarflow.repository.Repository;
import dev.neovoxel.neobot.NeoBot;
import dev.neovoxel.neobot.util.ListUtil;
import dev.neovoxel.nsapi.util.DatabaseStorageType;

import java.io.File;
import java.nio.file.Path;

public interface LibraryProvider {
    default void loadBasicLibrary(NeoBot plugin) throws Throwable {
        ExternalLoader.setClassLoader(Thread.currentThread().getContextClassLoader());
        File libDir = new File(plugin.getDataFolder(), "libs");
        JarFlow.setLibDir(libDir);
        Dependency json = Dependency.builder()
                .groupId("org.json")
                .artifactId("json")
                .version("20250517")
                .build();
        String fileName = json.getArtifactId() + "-" + json.getVersion();
        Path path = libDir.toPath().resolve(json.getGroupId()).resolve(json.getArtifactId()).resolve(json.getVersion()).resolve(fileName + ".jar");
        if (!hasDownloaded(libDir, json)) {
            Repository repository = Repository.mavenCentral();
            String downloadUrl = repository.getUrl() + json.getGroupId().replace(".", "/") +
                    "/" + json.getArtifactId() + "/" + json.getVersion() + "/" +
                    json.getArtifactId() + "-" + json.getVersion() + ".jar";
            DependencyDownloader.download(downloadUrl, json, libDir, 4);
        }
        JarFlow.getLoader().load(path.toFile());
        Dependency wsApi = Dependency.builder()
                .groupId("org.java-websocket")
                .artifactId("Java-WebSocket")
                .version("1.6.0")
                .build();
        Dependency nbApi = Dependency.builder()
                .groupId("dev.neovoxel.nbapi")
                .artifactId("NeoBotAPI")
                .version("1.2.3")
                .build();
        Dependency hikariCp;
        if (Float.parseFloat(System.getProperty("java.specification.version")) < 11) {
            hikariCp = Dependency.builder()
                    .groupId("com.zaxxer")
                    .artifactId("HikariCP")
                    .version("4.0.3")
                    .build();
        } else {
            hikariCp = Dependency.builder()
                    .groupId("com.zaxxer")
                    .artifactId("HikariCP")
                    .version("7.0.2")
                    .build();
        }
        JarFlow.addRepository(Repository.mavenCentral());
        JarFlow.addRepository(Repository.builder().url("https://maven.aliyun.com/repository/public").build());
        JarFlow.loadDependencies(ListUtil.of(wsApi, nbApi, hikariCp));
        if (Float.parseFloat(System.getProperty("java.specification.version")) < 17) {
            Dependency js = Dependency.builder()
                    .groupId("org.graalvm.js")
                    .artifactId("js")
                    .version("22.0.0.2")
                    .build();
            JarFlow.loadDependency(js);
        } else {
            Dependency js = Dependency.builder()
                    .groupId("org.graalvm.polyglot")
                    .artifactId("js")
                    .version("25.0.0")
                    .build();
            Dependency polyglot = Dependency.builder()
                    .groupId("org.graalvm.polyglot")
                    .artifactId("polyglot")
                    .version("25.0.0")
                    .build();
            JarFlow.loadDependencies(ListUtil.of(js, polyglot));
        }
    }

    default boolean hasDownloaded(File libDir, Dependency dependency) {
        Path path = libDir.toPath().resolve(dependency.getGroupId()).resolve(dependency.getArtifactId()).resolve(dependency.getVersion()).resolve(dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar");
        return path.toFile().exists();
    }

    default void loadStorageApi() throws Throwable {
        Dependency storageApi = Dependency.builder()
                .groupId("dev.neovoxel.nsapi")
                .artifactId("NeoStorageAPI")
                .version("1.1.0")
                .build();
        JarFlow.loadDependency(storageApi);
    }

    default void loadStorageLibrary(DatabaseStorageType type) throws Throwable {
        Dependency storage;
        if (type == DatabaseStorageType.SQLITE) {
            storage = Dependency.builder()
                    .groupId("org.xerial")
                    .artifactId("sqlite-jdbc")
                    .version("3.50.3.0")
                    .build();
        } else if (type == DatabaseStorageType.H2) {
            storage = Dependency.builder()
                    .groupId("com.h2database")
                    .artifactId("h2")
                    .version("2.2.224")
                    .build();
        } else if (type == DatabaseStorageType.MYSQL) {
            storage = Dependency.builder()
                    .groupId("com.mysql")
                    .artifactId("mysql-connector-j")
                    .version("8.2.0")
                    .build();
        } else if (type == DatabaseStorageType.MARIADB) {
            storage = Dependency.builder()
                    .groupId("org.mariadb.jdbc")
                    .artifactId("mariadb-java-client")
                    .version("3.1.2")
                    .build();
        } else {
            storage = Dependency.builder()
                    .groupId("org.postgresql")
                    .artifactId("postgresql")
                    .version("42.7.8")
                    .build();
        }
        JarFlow.loadDependency(storage);
    }
}
