/*
 * Cerberus  Copyright (C) 2013  vertigo17
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of Cerberus.
 *
 * Cerberus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Cerberus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Cerberus.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.cerberus.engine.execution.impl;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.cerberus.crud.entity.Invariant;
import org.cerberus.crud.entity.MessageGeneral;
import org.cerberus.crud.entity.Parameter;
import org.cerberus.crud.entity.RobotCapability;
import org.cerberus.crud.entity.Session;
import org.cerberus.crud.entity.SessionCapabilities;
import org.cerberus.crud.entity.TestCaseExecution;
import org.cerberus.crud.service.IInvariantService;
import org.cerberus.crud.service.IParameterService;
import org.cerberus.enums.MessageGeneralEnum;
import org.cerberus.exception.CerberusException;
import org.cerberus.engine.execution.ISeleniumServerService;
import org.cerberus.util.StringUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class SeleniumServerService implements ISeleniumServerService {

    @Autowired
    private IParameterService parameterService;
    @Autowired
    private IInvariantService invariantService;

    private static final org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(SeleniumServerService.class);

    @Override
    public void startServer(TestCaseExecution tCExecution) throws CerberusException {
        //message used for log purposes 
        String logPrefix = "[" + tCExecution.getTest() + " - " + tCExecution.getTestCase() + "] ";

        try {

            LOG.info(logPrefix + "Start Selenium Server");

            /**
             * Set Session
             */
            LOG.debug(logPrefix + "Setting the session.");
            long defaultWait;
            try {
                Parameter param = parameterService.findParameterByKey("selenium_defaultWait", tCExecution.getApplicationObj().getSystem());
                String to = tCExecution.getTimeout().equals("") ? param.getValue() : tCExecution.getTimeout();
                defaultWait = Long.parseLong(to);
            } catch (CerberusException ex) {
                LOG.warn(logPrefix + "Parameter (selenium_defaultWait) not in Parameter table, default wait set to 90 seconds. " + ex.toString());
                defaultWait = 90;
            }
            LOG.debug(logPrefix + "TimeOut defined on session : " + defaultWait);
            List<SessionCapabilities> capabilities = new ArrayList();
            SessionCapabilities sc = new SessionCapabilities();
            sc.create("browser", tCExecution.getBrowser());
            capabilities.add(sc);
            sc = new SessionCapabilities();
            sc.create("platform", tCExecution.getPlatform());
            capabilities.add(sc);
            sc = new SessionCapabilities();
            sc.create("version", tCExecution.getVersion());
            capabilities.add(sc);

            // Add additional capabilities if necessary
            List<RobotCapability> additionalCapabilities = tCExecution.getCapabilities();
            if (additionalCapabilities != null) {
                for (RobotCapability additionalCapability : additionalCapabilities) {
                    sc = new SessionCapabilities();
                    sc.create(additionalCapability.getCapability(), additionalCapability.getValue());
                    capabilities.add(sc);
                }
            }

            Session session = new Session();
            session.setDefaultWait(defaultWait);
            session.setHost(tCExecution.getSeleniumIP());
            session.setPort(tCExecution.getPort());
            session.setCapabilities(capabilities);

            tCExecution.setSession(session);
            LOG.debug(logPrefix + "Session is set.");

            /**
             * SetUp Capabilities
             */
            LOG.debug(logPrefix + "Set Capabilities");
            DesiredCapabilities caps = this.setCapabilities(tCExecution);

            /**
             * SetUp Driver
             */
            LOG.debug(logPrefix + "Set Driver");
            WebDriver driver = null;
            AppiumDriver appiumDriver = null;
            if (tCExecution.getApplicationObj().getType().equalsIgnoreCase("GUI")) {
                if (caps.getPlatform().is(Platform.ANDROID)) {
                    appiumDriver = new AndroidDriver(new URL("http://" + tCExecution.getSession().getHost() + ":" + tCExecution.getSession().getPort() + "/wd/hub"), caps);
                    driver = (WebDriver) appiumDriver;
                } else if (caps.getPlatform().is(Platform.MAC)) {
                    appiumDriver = new IOSDriver(new URL("http://" + tCExecution.getSession().getHost() + ":" + tCExecution.getSession().getPort() + "/wd/hub"), caps);
                    driver = (WebDriver) appiumDriver;
                } else {
                    driver = new RemoteWebDriver(new URL("http://" + tCExecution.getSession().getHost() + ":" + tCExecution.getSession().getPort() + "/wd/hub"), caps);
                }
            } else if (tCExecution.getApplicationObj().getType().equalsIgnoreCase("APK")) {
                appiumDriver = new AndroidDriver(new URL("http://" + tCExecution.getSession().getHost() + ":" + tCExecution.getSession().getPort() + "/wd/hub"), caps);
                driver = (WebDriver) appiumDriver;
            } else if (tCExecution.getApplicationObj().getType().equalsIgnoreCase("IPA")) {
                appiumDriver = new IOSDriver(new URL("http://" + tCExecution.getSession().getHost() + ":" + tCExecution.getSession().getPort() + "/wd/hub"), caps);
                driver = (WebDriver) appiumDriver;
            }

            /**
             * Defining the timeout at the driver level. Only in case of not
             * Appium Driver (see
             * https://github.com/vertigo17/Cerberus/issues/754)
             */
            if (driver != null && appiumDriver == null) {
                driver.manage().timeouts().pageLoadTimeout(tCExecution.getSession().getDefaultWait(), TimeUnit.SECONDS);
                driver.manage().timeouts().implicitlyWait(tCExecution.getSession().getDefaultWait(), TimeUnit.SECONDS);
                driver.manage().timeouts().setScriptTimeout(tCExecution.getSession().getDefaultWait(), TimeUnit.SECONDS);
            }
            tCExecution.getSession().setDriver(driver);
            tCExecution.getSession().setAppiumDriver(appiumDriver);

            /**
             * If Gui application, maximize window Get IP of Node in case of
             * remote Server
             */
            if (tCExecution.getApplicationObj().getType().equalsIgnoreCase("GUI")
                    && !caps.getPlatform().equals(Platform.ANDROID)) {
                driver.manage().window().maximize();
                getIPOfNode(tCExecution);

                /**
                 * If screenSize is defined, set the size of the screen.
                 */
                if (!tCExecution.getScreenSize().equals("")) {
                    Integer screenWidth = Integer.valueOf(tCExecution.getScreenSize().split("\\*")[0]);
                    Integer screenLength = Integer.valueOf(tCExecution.getScreenSize().split("\\*")[1]);
                    setScreenSize(driver, screenWidth, screenLength);
                }
                tCExecution.setScreenSize(getScreenSize(driver));
            }
            tCExecution.getSession().setStarted(true);

        } catch (CerberusException exception) {
            LOG.error(logPrefix + exception.toString());
            throw new CerberusException(exception.getMessageError());
        } catch (MalformedURLException exception) {
            LOG.error(logPrefix + exception.toString());
            MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.VALIDATION_FAILED_URL_MALFORMED);
            mes.setDescription(mes.getDescription().replace("%URL%", tCExecution.getSession().getHost() + ":" + tCExecution.getSession().getPort()));
            throw new CerberusException(mes);
        } catch (UnreachableBrowserException exception) {
            LOG.error(logPrefix + exception.toString());
            MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.VALIDATION_FAILED_SELENIUM_COULDNOTCONNECT);
            mes.setDescription(mes.getDescription().replace("%SSIP%", tCExecution.getSeleniumIP()));
            mes.setDescription(mes.getDescription().replace("%SSPORT%", tCExecution.getSeleniumPort()));
            throw new CerberusException(mes);
        } catch (Exception exception) {
            LOG.error(logPrefix + exception.toString());
            MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
            mes.setDescription(mes.getDescription().replace("%MES%", exception.toString()));
            throw new CerberusException(mes);
        }
    }

    private DesiredCapabilities setFirefoxProfile(TestCaseExecution tCExecution) throws CerberusException {
        FirefoxProfile profile = new FirefoxProfile();
        profile.setEnableNativeEvents(true);
        profile.setAcceptUntrustedCertificates(true);
        profile.setPreference("network.http.connection-timeout", "300");

        try {
            Invariant invariant = this.invariantService.findInvariantByIdValue("COUNTRY", tCExecution.getCountry());
            if (invariant.getGp2() == null) {
                LOG.warn("Country selected (" + tCExecution.getCountry() + ") has no value of GP2 in Invariant table, default language set to English (en)");
                profile.setPreference("intl.accept_languages", "en");
            } else {
                profile.setPreference("intl.accept_languages", invariant.getGp2());
            }
        } catch (CerberusException ex) {
            LOG.warn("Country selected (" + tCExecution.getCountry() + ") not in Invariant table, default language set to English (en)");
            profile.setPreference("intl.accept_languages", "en");
        }

        if (tCExecution.getVerbose() > 0) {
            String firebugPath = parameterService.findParameterByKey("cerberus_selenium_firefoxextension_firebug", "").getValue();
            String netexportPath = parameterService.findParameterByKey("cerberus_selenium_firefoxextension_netexport", "").getValue();
            if (StringUtil.isNullOrEmpty(firebugPath)) {
                MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
                mes.setDescription(mes.getDescription().replace("%MES%", "Mandatory parameter for network traffic 'cerberus_selenium_firefoxextension_firebug' not defined."));
                throw new CerberusException(mes);
            }
            if (StringUtil.isNullOrEmpty(netexportPath)) {
                MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
                mes.setDescription(mes.getDescription().replace("%MES%", "Mandatory parameter for network traffic 'cerberus_selenium_firefoxextension_netexport' not defined."));
                throw new CerberusException(mes);
            }

            File firebug = new File(firebugPath);
            if (!firebug.canRead()) {
                LOG.warn("Can't read : " + firebugPath);
                MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
                mes.setDescription(mes.getDescription().replace("%MES%", "File not found : '" + firebugPath + "' Change the Cerberus parameter : cerberus_selenium_firefoxextension_firebug"));
                throw new CerberusException(mes);

            }
            try {
                LOG.debug("Adding firebug extension : " + firebugPath);
                profile.addExtension(firebug);
            } catch (IOException exception) {
                LOG.warn(exception.toString());
                MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
                mes.setDescription(mes.getDescription().replace("%MES%", "File not found : " + firebugPath));
                throw new CerberusException(mes);
            }

            File netExport = new File(netexportPath);
            if (!netExport.canRead()) {
                LOG.warn("Can't read : " + netexportPath);
                MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
                mes.setDescription(mes.getDescription().replace("%MES%", "File not found : " + netexportPath + "' Change the Cerberus parameter : cerberus_selenium_firefoxextension_netexport"));
                throw new CerberusException(mes);
            }
            try {
                LOG.debug("Adding netexport extension : " + netexportPath);
                profile.addExtension(netExport);
            } catch (IOException exception) {
                LOG.warn(exception.toString());
                MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
                mes.setDescription(mes.getDescription().replace("%MES%", "File not found : " + netexportPath));
                throw new CerberusException(mes);
            }

            String cerberusUrl = parameterService.findParameterByKey("cerberus_url", "").getValue();
            if (StringUtil.isNullOrEmpty(cerberusUrl)) {
                MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
                mes.setDescription(mes.getDescription().replace("%MES%", "Mandatory parameter for network traffic 'cerberus_url' not defined."));
                throw new CerberusException(mes);
            }

            // Set default Firefox preferences
            profile.setPreference("app.update.enabled", false);

            // Set default Firebug preferences
            profile.setPreference("extensions.firebug.currentVersion", "1.11.4");
            profile.setPreference("extensions.firebug.allPagesActivation", "on");
            profile.setPreference("extensions.firebug.defaultPanelName", "net");
            profile.setPreference("extensions.firebug.net.enableSites", true);

            // Set default NetExport preferences
            profile.setPreference("extensions.firebug.netexport.alwaysEnableAutoExport", true);
            // Export to Server.
            String url = cerberusUrl + "/SaveStatistic?logId=" + tCExecution.getExecutionUUID();
            profile.setPreference("extensions.firebug.netexport.autoExportToServer", true);
            profile.setPreference("extensions.firebug.netexport.beaconServerURL", url);
            LOG.debug("Selenium netexport.beaconServerURL : " + url);
            profile.setPreference("extensions.firebug.netexport.sendToConfirmation", false);
            profile.setPreference("extensions.firebug.netexport.showPreview", false);

        }

        //if userAgent
        if (!StringUtil.isNullOrEmpty(tCExecution.getTestCaseObj().getUserAgent())) {
            profile.setPreference("general.useragent.override", tCExecution.getTestCaseObj().getUserAgent());
        } else if (!("").equals(tCExecution.getUserAgent())) {
            profile.setPreference("general.useragent.override", tCExecution.getUserAgent());
        }

        DesiredCapabilities dc = DesiredCapabilities.firefox();
        dc.setCapability(FirefoxDriver.PROFILE, profile);

        return dc;
    }

    private DesiredCapabilities setCapabilities(TestCaseExecution tCExecution) throws CerberusException {
        DesiredCapabilities caps = new DesiredCapabilities();

        // First, add all capabilities from test case execution
        for (SessionCapabilities cap : tCExecution.getSession().getCapabilities()) {
            // Only those with valid value
            if (StringUtil.isNullOrEmpty(cap.getValue())) {
                continue;
            }

            // Special case if capability if the browser
            if (tCExecution.getApplicationObj().getType().equalsIgnoreCase("GUI") && cap.getCapability().equalsIgnoreCase("browser")) {
                caps = this.setCapabilityBrowser(caps, cap.getValue(), tCExecution);
                continue;
            }

            // Otherwise, add the capability as is
            caps.setCapability(cap.getCapability(), cap.getValue());
        }

        // Second, if application is a mobile one, then set the "app" capability to the application binary path
        if (tCExecution.getApplicationObj().getType().equalsIgnoreCase("APK")
                || tCExecution.getApplicationObj().getType().equalsIgnoreCase("IPA")) {
            // Set the app capability with the application path
            caps.setCapability("app", tCExecution.getCountryEnvironmentParameters().getIp());
        }

        return caps;
    }

    @Override
    public boolean stopServer(Session session) {
        if (session.isStarted()) {
            try {
                // Wait 2 sec till HAR is exported
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                LOG.error(ex.toString());
            }
            LOG.info("Stop execution session");
            session.quit();
            return true;
        }
        return false;
    }

    private static void getIPOfNode(TestCaseExecution tCExecution) {
        try {
            Session session = tCExecution.getSession();
            HttpCommandExecutor ce = (HttpCommandExecutor) ((RemoteWebDriver) session.getDriver()).getCommandExecutor();
            SessionId sessionId = ((RemoteWebDriver) session.getDriver()).getSessionId();
            String hostName = ce.getAddressOfRemoteServer().getHost();
            int port = ce.getAddressOfRemoteServer().getPort();
            HttpHost host = new HttpHost(hostName, port);
            HttpClient client = HttpClientBuilder.create().build();
            URL sessionURL = new URL("http://" + session.getHost() + ":" + session.getPort() + "/grid/api/testsession?session=" + sessionId);
            BasicHttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", sessionURL.toExternalForm());
            HttpResponse response = client.execute(host, r);
            if (!response.getStatusLine().toString().contains("403")) {
                InputStream contents = response.getEntity().getContent();
                StringWriter writer = new StringWriter();
                IOUtils.copy(contents, writer, "UTF8");
                JSONObject object = new JSONObject(writer.toString());
                URL myURL = new URL(object.getString("proxyId"));
                if ((myURL.getHost() != null) && (myURL.getPort() != -1)) {
                    tCExecution.setIp(myURL.getHost());
                    tCExecution.setPort(String.valueOf(myURL.getPort()));
                }
            }

        } catch (IOException ex) {
            LOG.error(ex.toString());
        } catch (JSONException ex) {
            LOG.error(ex.toString());
        }
    }

    public DesiredCapabilities setCapabilityBrowser(DesiredCapabilities capabilities, String browser, TestCaseExecution tCExecution) throws CerberusException {
        try {
            if (browser.equalsIgnoreCase("firefox")) {
                capabilities = this.setFirefoxProfile(tCExecution);
            } else if (browser.equalsIgnoreCase("IE")) {
                capabilities = DesiredCapabilities.internetExplorer();
            } else if (browser.equalsIgnoreCase("chrome")) {
                capabilities = DesiredCapabilities.chrome();
            } else if (browser.contains("android")) {
                capabilities = DesiredCapabilities.android();
            } else if (browser.contains("ipad")) {
                capabilities = DesiredCapabilities.ipad();
            } else if (browser.contains("iphone")) {
                capabilities = DesiredCapabilities.iphone();
            } else if (browser.contains("opera")) {
                capabilities = DesiredCapabilities.opera();
            } else if (browser.contains("safari")) {
                capabilities = DesiredCapabilities.safari();
            } else {
                LOG.warn("Not supported Browser : " + browser);
                MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
                mes.setDescription(mes.getDescription().replace("%MES%", "Browser '" + browser + "' is not supported"));
                mes.setDescription("Not supported Browser : " + browser);
                throw new CerberusException(mes);
            }
        } catch (CerberusException ex) {
            MessageGeneral mes = new MessageGeneral(MessageGeneralEnum.EXECUTION_FA_SELENIUM);
            mes.setDescription(mes.getDescription().replace("%MES%", "Failed to set capability on the browser '" + browser + "'"));
            throw new CerberusException(mes);
        }
        return capabilities;
    }

    @Override
    public Capabilities getUsedCapabilities(Session session) {
        Capabilities caps = ((RemoteWebDriver) session.getDriver()).getCapabilities();
        return caps;
    }

    private void setScreenSize(WebDriver driver, Integer width, Integer length) {
        driver.manage().window().setPosition(new Point(0, 0));
        driver.manage().window().setSize(new Dimension(width, length));
    }

    private String getScreenSize(WebDriver driver) {
        return driver.manage().window().getSize().toString();
    }
}
