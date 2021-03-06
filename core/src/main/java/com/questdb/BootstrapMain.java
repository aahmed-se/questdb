/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2016 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb;

import com.questdb.factory.JournalFactory;
import com.questdb.factory.JournalFactoryPool;
import com.questdb.log.*;
import com.questdb.misc.Misc;
import com.questdb.misc.Os;
import com.questdb.mp.RingQueue;
import com.questdb.mp.Sequence;
import com.questdb.net.http.HttpServer;
import com.questdb.net.http.ServerConfiguration;
import com.questdb.net.http.SimpleUrlMatcher;
import com.questdb.net.http.handlers.*;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

class BootstrapMain {

    public static void main(String[] args) throws Exception {
        System.err.printf("QuestDB HTTP Server 1.0%nCopyright (C) Appsicle 2014-2016, all rights reserved.%n%n");
        if (args.length < 1) {
            System.err.println("Root directory name expected");
            return;
        }

        if (Os.type == Os._32Bit) {
            System.err.println("QuestDB requires 64-bit JVM");
            return;
        }

        String dir = args[0];
        String flag = args.length > 1 ? args[1] : null;

        extractSite(dir, "-f".equals(flag));
        File conf = new File(dir, "conf/questdb.conf");

        if (!conf.exists()) {
            System.err.println("Configuration file does not exist: " + conf);
            return;
        }

        final ServerConfiguration configuration = new ServerConfiguration(conf);
        configureLoggers(configuration);

        final SimpleUrlMatcher matcher = new SimpleUrlMatcher();
        JournalFactory factory = new JournalFactory(configuration.getDbPath().getAbsolutePath());
        JournalFactoryPool pool = new JournalFactoryPool(factory.getConfiguration(), configuration.getJournalPoolSize());
        matcher.put("/imp", new ImportHandler(factory));
        matcher.put("/js", new QueryHandler(pool, configuration, factory));
        matcher.put("/csv", new CsvHandler(pool, configuration));
        matcher.put("/chk", new ExistenceCheckHandler(factory));
        matcher.setDefaultHandler(new StaticContentHandler(configuration));

        StringBuilder welcome = Misc.getThreadLocalBuilder();
        HttpServer server = new HttpServer(configuration, matcher);
        if (!server.start(LogFactory.INSTANCE.getJobs(), configuration.getHttpQueueDepth())) {
            welcome.append("Could not bind socket ").append(configuration.getHttpIP()).append(':').append(configuration.getHttpPort());
            welcome.append(". Already running?");
            System.err.println(welcome);
            System.out.println(new Date() + " QuestDB failed to start");
        } else {
            welcome.append("Listening on ").append(configuration.getHttpIP()).append(':').append(configuration.getHttpPort());
            if (configuration.getSslConfig().isSecure()) {
                welcome.append(" [HTTPS]");
            } else {
                welcome.append(" [HTTP plain]");
            }

            System.err.println(welcome);
            System.out.println(new Date() + " QuestDB is running");

            if (Os.type != Os.WINDOWS) {
                // suppress HUP signal
                Signal.handle(new Signal("HUP"), new SignalHandler() {
                    public void handle(Signal signal) {
                    }
                });
            }

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    System.out.println(new Date() + " QuestDB is shutting down");
                }
            }));
        }
    }

    private static void configureLoggers(final ServerConfiguration configuration) {
        LogFactory.INSTANCE.add(new LogWriterConfig("access", LogLevel.LOG_LEVEL_ALL, new LogWriterFactory() {
            @Override
            public LogWriter createLogWriter(RingQueue<LogRecordSink> ring, Sequence seq, int level) {
                LogFileWriter w = new LogFileWriter(ring, seq, level);
                w.setLocation(configuration.getAccessLog().getAbsolutePath());
                return w;
            }
        }));

        final int level = System.getProperty(LogFactory.DEBUG_TRIGGER) != null ? LogLevel.LOG_LEVEL_ALL : LogLevel.LOG_LEVEL_ERROR | LogLevel.LOG_LEVEL_INFO;
        LogFactory.INSTANCE.add(new LogWriterConfig(level,
                new LogWriterFactory() {
                    @Override
                    public LogWriter createLogWriter(RingQueue<LogRecordSink> ring, Sequence seq, int level) {
                        LogFileWriter w = new LogFileWriter(ring, seq, level);
                        w.setLocation(configuration.getErrorLog().getAbsolutePath());
                        return w;
                    }
                }));

        LogFactory.INSTANCE.bind();
    }

    private static void extractSite(String dir, boolean force) throws URISyntaxException, IOException {
        System.out.println("Preparing content...");
        URL url = HttpServer.class.getResource("/site/");
        String[] components = url.toURI().toString().split("!");
        FileSystem fs = null;
        final Path source;
        final int sourceLen;
        if (components.length > 1) {
            fs = FileSystems.newFileSystem(URI.create(components[0]), new HashMap<String, Object>());
            source = fs.getPath(components[1]);
            sourceLen = source.toAbsolutePath().toString().length();
        } else {
            source = Paths.get(url.toURI());
            sourceLen = source.toAbsolutePath().toString().length() + 1;
        }

        try {
            final Path target = Paths.get(dir);
            final EnumSet<FileVisitOption> walkOptions = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
            final CopyOption[] copyOptions = new CopyOption[]{COPY_ATTRIBUTES, REPLACE_EXISTING};

            if (force) {
                File pub = new File(dir, "public");
                if (pub.exists()) {
                    com.questdb.misc.Files.delete(pub);
                }
            }

            Files.walkFileTree(source, walkOptions, Integer.MAX_VALUE, new FileVisitor<Path>() {

                private boolean skip = true;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (skip) {
                        skip = false;
                    } else {
                        try {
                            Files.copy(dir, toDestination(dir), copyOptions);
                            System.out.println("Extracted " + dir);
                        } catch (FileAlreadyExistsException ignore) {
                        } catch (IOException x) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, toDestination(file), copyOptions);
                    System.out.println("Extracted " + file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                private Path toDestination(final Path path) {
                    final Path tmp = path.toAbsolutePath();
                    return target.resolve(tmp.toString().substring(sourceLen));
                }
            });
        } finally {
            if (fs != null) {
                fs.close();
            }
        }
    }

}
