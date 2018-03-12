/*
 * Copyright 2013 Proofpoint Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.kairosdb.core.http;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.eclipse.jetty.http.HttpVersion.HTTP_1_1;
import static org.kairosdb.util.Preconditions.checkNotNullOrEmpty;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.regex.Pattern;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import io.opentracing.util.GlobalTracer;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.kairosdb.core.KairosDBService;
import org.kairosdb.core.exception.KairosDBException;
import org.kairosdb.core.health.HealthCheckResource;
import org.kairosdb.core.http.rest.MetricsResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.servlet.GuiceFilter;

import javax.ws.rs.core.MediaType;


public class WebServer implements KairosDBService {
    public static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    public static final String JETTY_ADDRESS_PROPERTY = "kairosdb.jetty.address";
    public static final String JETTY_PORT_PROPERTY = "kairosdb.jetty.port";
    public static final String JETTY_WEB_ROOT_PROPERTY = "kairosdb.jetty.static_web_root";
    public static final String JETTY_AUTH_USER_PROPERTY = "kairosdb.jetty.basic_auth.user";
    public static final String JETTY_AUTH_PASSWORD_PROPERTY = "kairosdb.jetty.basic_auth.password";
    public static final String JETTY_SSL_PORT = "kairosdb.jetty.ssl.port";
    public static final String JETTY_SSL_PROTOCOLS = "kairosdb.jetty.ssl.protocols";
    public static final String JETTY_SSL_CIPHER_SUITES = "kairosdb.jetty.ssl.cipherSuites";
    public static final String JETTY_SSL_KEYSTORE_PATH = "kairosdb.jetty.ssl.keystore.path";
    public static final String JETTY_SSL_KEYSTORE_PASSWORD = "kairosdb.jetty.ssl.keystore.password";

    private InetAddress m_address;
    private int m_port;
    private String m_webRoot;
    private Server m_server;
    private String m_authUser = null;
    private String m_authPassword = null;
    private int m_sslPort;
    private String[] m_cipherSuites;
    private String[] m_protocols;
    private String m_keyStorePath;
    private String m_keyStorePassword;

    public WebServer(int port, String webRoot)
            throws UnknownHostException {
        this(null, port, webRoot);
    }

    @Inject
    public WebServer(@Named(JETTY_ADDRESS_PROPERTY) String address,
                     @Named(JETTY_PORT_PROPERTY) int port,
                     @Named(JETTY_WEB_ROOT_PROPERTY) String webRoot)
            throws UnknownHostException {
        checkNotNull(webRoot);

        m_port = port;
        m_webRoot = webRoot;
        m_address = InetAddress.getByName(address);
    }

    @Inject(optional = true)
    public void setAuthCredentials(@Named(JETTY_AUTH_USER_PROPERTY) String user,
                                   @Named(JETTY_AUTH_PASSWORD_PROPERTY) String password) {
        m_authUser = user;
        m_authPassword = password;
    }

    @Inject(optional = true)
    public void setSSLSettings(@Named(JETTY_SSL_PORT) int sslPort,
                               @Named(JETTY_SSL_KEYSTORE_PATH) String keyStorePath,
                               @Named(JETTY_SSL_KEYSTORE_PASSWORD) String keyStorePassword) {
        m_sslPort = sslPort;
        m_keyStorePath = checkNotNullOrEmpty(keyStorePath);
        m_keyStorePassword = checkNotNullOrEmpty(keyStorePassword);
    }

    @Inject(optional = true)
    public void setSSLCipherSuites(@Named(JETTY_SSL_CIPHER_SUITES) String cipherSuites) {
        checkNotNull(cipherSuites);
        m_cipherSuites = cipherSuites.split("\\s*,\\s*");
    }

    @Inject(optional = true)
    public void setSSLProtocols(@Named(JETTY_SSL_PROTOCOLS) String protocols) {
        m_protocols = protocols.split("\\s*,\\s*");
    }

    @Override
    public void start() throws KairosDBException {
        try {
            ResourceConfig config = new ResourceConfig();
            config.register(MetricsResource.class);
            config.register(JacksonJsonProvider.class);
            config.register(HealthCheckResource.class);

            ServletContainer container = new ServletContainer(config);
            ServletHolder servletHolder = new ServletHolder(container);

            if (m_port > 0)
                m_server = new Server(new InetSocketAddress(m_address, m_port));
            else
                m_server = new Server();

            //Set up SSL
            if (m_keyStorePath != null && !m_keyStorePath.isEmpty()) {
                logger.info("Using SSL");
                SslContextFactory sslContextFactory = new SslContextFactory(m_keyStorePath);

                if (m_cipherSuites != null && m_cipherSuites.length > 0)
                    sslContextFactory.setIncludeCipherSuites(m_cipherSuites);

                if (m_protocols != null && m_protocols.length > 0)
                    sslContextFactory.setIncludeProtocols(m_protocols);

                sslContextFactory.setKeyStorePassword(m_keyStorePassword);

                ServerConnector sslConnector = new ServerConnector(m_server,
                        new SslConnectionFactory(sslContextFactory, HTTP_1_1.asString()));
                sslConnector.setPort(m_sslPort);
                m_server.addConnector(sslConnector);
            }

            ServletContextHandler servletContextHandler = new ServletContextHandler();

            //Turn on basic auth if the user was specified
            if (m_authUser != null) {
                servletContextHandler.setSecurityHandler(basicAuth(m_authUser, m_authPassword, "kairos"));
                servletContextHandler.setContextPath("/");
            }

            FilterHolder guiceHolder = new FilterHolder(GuiceFilter.class);
            servletContextHandler.addFilter(guiceHolder, "/*", null);

            TracingFilter filter = new TracingFilter(GlobalTracer.get(),
                    Collections.singletonList(ServletFilterSpanDecorator.STANDARD_TAGS),
                    Pattern.compile(
                            "/api/v1/health/check|/api-docs.*|/autoconfig|/configprops|/dump|/health|/info|/metrics.*|" +
                                    "/mappings|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico|/hystrix.stream"));

            FilterHolder tracerHolder = new FilterHolder(filter);
            servletContextHandler.addFilter(tracerHolder, "/*", null);//EnumSet.allOf(DispatcherType.class));

            GzipHandler gzipHander = new GzipHandler();
            gzipHander.addIncludedMethods("GET", "POST");
            gzipHander.addIncludedMimeTypes(MediaType.APPLICATION_JSON);
            gzipHander.addIncludedPaths();

            ResourceHandler resourceHandler = new ResourceHandler();
            resourceHandler.setDirectoriesListed(true);
            resourceHandler.setWelcomeFiles(new String[]{"index.html"});
            resourceHandler.setResourceBase(m_webRoot);
            //resourceHandler.setAliases(true);

            HandlerList handlers = new HandlerList();
            handlers.setHandlers(new Handler[]{servletContextHandler, resourceHandler, new DefaultHandler()});

            servletContextHandler.addServlet(servletHolder, "/*");

            m_server.setHandler(handlers);
            m_server.start();
        } catch (Exception e) {
            throw new KairosDBException(e);
        }
    }

    @Override
    public void stop() {
        try {
            if (m_server != null) {
                m_server.stop();
                // m_server.join();
                // to avoid NPE
                if (m_server.getThreadPool() != null) {
                    m_server.getThreadPool().join();
                }
            }
        } catch (Exception e) {
            logger.error("Error stopping web server", e);
        }
    }

    public InetAddress getAddress() {
        return m_address;
    }

    private static SecurityHandler basicAuth(String username, String password, String realm) {
        HashLoginService l = new HashLoginService();
        UserStore userStore = new UserStore();
        userStore.addUser(username, Credential.getCredential(password), new String[]{"user"});
        l.setUserStore(userStore);
        l.setName(realm);

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("myrealm");
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;
    }
}
