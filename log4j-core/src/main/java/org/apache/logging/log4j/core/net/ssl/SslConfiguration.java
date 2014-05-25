/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.net.ssl;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.status.StatusLogger;

/**
 *  SSL Configuration
 */
@Plugin(name = "Ssl", category = "Core", printObject = true)
public class SslConfiguration {
    private static final StatusLogger LOGGER = StatusLogger.getLogger();
    private final KeyStoreConfiguration keyStoreConfig;
    private final TrustStoreConfiguration trustStoreConfig;
    private final SSLContext sslContext;
    private final String protocol;

    private SslConfiguration(String protocol, KeyStoreConfiguration keyStoreConfig,
            TrustStoreConfiguration trustStoreConfig) {
        this.keyStoreConfig = keyStoreConfig;
        this.trustStoreConfig = trustStoreConfig;
        this.protocol = protocol == null ? SslConfigurationDefaults.PROTOCOL : protocol;
        this.sslContext = this.createSslContext();
    }

    public SSLSocketFactory getSslSocketFactory() {
        return sslContext.getSocketFactory();
    }

    public SSLServerSocketFactory getSslServerSocketFactory() {
        return sslContext.getServerSocketFactory();
    }

    private SSLContext createSslContext() {
        SSLContext context = null;

        try {
            context = createSslContextBasedOnConfiguration();
            LOGGER.debug("Creating SSLContext with the given parameters");
        }
        catch (TrustStoreConfigurationException e) {
            context = createSslContextWithTrustStoreFailure();
        }
        catch (KeyStoreConfigurationException e) {
            context = createSslContextWithKeyStoreFailure();
        }
        return context;
    }

    private SSLContext createSslContextWithTrustStoreFailure() {
        SSLContext context;

        try {
            context = createSslContextWithDefaultTrustManagerFactory();
            LOGGER.debug("Creating SSLContext with default truststore");
        }
        catch (KeyStoreConfigurationException e) {
            context = createDefaultSslContext();
            LOGGER.debug("Creating SSLContext with default configuration");
        }
        return context;
    }

    private SSLContext createSslContextWithKeyStoreFailure() {
        SSLContext context;

        try {
            context = createSslContextWithDefaultKeyManagerFactory();
            LOGGER.debug("Creating SSLContext with default keystore");
        }
        catch (TrustStoreConfigurationException e) {
            context = createDefaultSslContext();
            LOGGER.debug("Creating SSLContext with default configuration");
        }
        return context;
    }

    private SSLContext createSslContextBasedOnConfiguration() throws KeyStoreConfigurationException, TrustStoreConfigurationException {
        return createSslContext(false, false);
    }

    private SSLContext createSslContextWithDefaultKeyManagerFactory() throws TrustStoreConfigurationException {
        try {
            return createSslContext(true, false);
        } catch (KeyStoreConfigurationException dummy) {
             LOGGER.debug("Exception occured while using default keystore. This should be a BUG");
             return null;
        }
    }

    private SSLContext createSslContextWithDefaultTrustManagerFactory() throws KeyStoreConfigurationException {
        try {
            return createSslContext(false, true);
        }
        catch (TrustStoreConfigurationException dummy) {
            LOGGER.debug("Exception occured while using default truststore. This should be a BUG");
            return null;
        }
    }

    private SSLContext createDefaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Failed to create an SSLContext with default configuration");
            return null;
        }
    }

    private SSLContext createSslContext(boolean loadDefaultKeyManagerFactory, boolean loadDefaultTrustManagerFactory)
            throws KeyStoreConfigurationException, TrustStoreConfigurationException {
        try {
            KeyManager[] kManagers = null;
            TrustManager[] tManagers = null;

            SSLContext newSslContext = SSLContext.getInstance(this.protocol);
            if (!loadDefaultKeyManagerFactory) {
                KeyManagerFactory kmFactory = loadKeyManagerFactory();
                kManagers = kmFactory.getKeyManagers();
            }
            if (!loadDefaultTrustManagerFactory) {
                TrustManagerFactory tmFactory = loadTrustManagerFactory();
                tManagers = tmFactory.getTrustManagers();
            }

            newSslContext.init(kManagers, tManagers, null);
            return newSslContext;
        }
        catch (NoSuchAlgorithmException e) {
            LOGGER.error("No Provider supports a TrustManagerFactorySpi implementation for the specified protocol");
            throw new TrustStoreConfigurationException(e);
        }
        catch (KeyManagementException e) {
            LOGGER.error("Failed to initialize the SSLContext");
            throw new KeyStoreConfigurationException(e);
        }
    }

    private TrustManagerFactory loadTrustManagerFactory() throws TrustStoreConfigurationException {
        if (trustStoreConfig == null) {
            throw new TrustStoreConfigurationException(new Exception("The trustStoreConfiguration is null"));
        }

        try {
            return trustStoreConfig.initTrustManagerFactory();
        }
        catch (NoSuchAlgorithmException e) {
            LOGGER.error("The specified algorithm is not available from the specified provider");
            throw new TrustStoreConfigurationException(e);
        } catch (KeyStoreException e) {
            LOGGER.error("Failed to initialize the TrustManagerFactory");
            throw new TrustStoreConfigurationException(e);
        }
    }

    private KeyManagerFactory loadKeyManagerFactory() throws KeyStoreConfigurationException {
        if (keyStoreConfig == null) {
            throw new KeyStoreConfigurationException(new Exception("The keyStoreConfiguration is null"));
        }

        try {
            return keyStoreConfig.initKeyManagerFactory();
        }
        catch (NoSuchAlgorithmException e) {
            LOGGER.error("The specified algorithm is not available from the specified provider");
            throw new KeyStoreConfigurationException(e);
        } catch (KeyStoreException e) {
            LOGGER.error("Failed to initialize the TrustManagerFactory");
            throw new KeyStoreConfigurationException(e);
        } catch (UnrecoverableKeyException e) {
            LOGGER.error("The key cannot be recovered (e.g. the given password is wrong)");
            throw new KeyStoreConfigurationException(e);
        }
    }

    public boolean equals(SslConfiguration config) {
        if (config == null) {
            return false;
        }

        boolean keyStoreEquals = false;
        boolean trustStoreEquals = false;

        if (keyStoreConfig != null) {
            keyStoreEquals = keyStoreConfig.equals(config.keyStoreConfig);
        } else {
            keyStoreEquals = keyStoreConfig == config.keyStoreConfig;
        }

        if (trustStoreConfig != null) {
            trustStoreEquals = trustStoreConfig.equals(config.trustStoreConfig);
        } else {
            trustStoreEquals = trustStoreConfig == config.trustStoreConfig;
        }

        return keyStoreEquals && trustStoreEquals;
    }

    /**
     * Creates an SslConfiguration from a KeyStoreConfiguration and a TrustStoreConfiguration.
     * @param protocol The protocol, see http://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#SSLContext
     * @param keyStoreConfig The KeyStoreConfiguration.
     * @param trustStoreConfig The TrustStoreConfiguration.
     * @return a new SslConfiguration
     */
    @PluginFactory
    public static SslConfiguration createSSLConfiguration(
            // @formatter:off
            @PluginAttribute("protocol") String protocol,
            @PluginElement("KeyStore") KeyStoreConfiguration keyStoreConfig, 
            @PluginElement("TrustStore") TrustStoreConfiguration trustStoreConfig) {
            // @formatter:on
        return new SslConfiguration(protocol, keyStoreConfig, trustStoreConfig);
    }
}