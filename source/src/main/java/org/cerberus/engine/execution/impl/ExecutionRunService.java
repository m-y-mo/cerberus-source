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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import org.apache.log4j.Level;
import org.cerberus.crud.entity.CountryEnvLink;
import org.cerberus.crud.entity.CountryEnvParam;
import org.cerberus.engine.entity.ExecutionUUID;
import org.cerberus.crud.entity.MessageEvent;
import org.cerberus.enums.MessageEventEnum;
import org.cerberus.crud.entity.MessageGeneral;
import org.cerberus.enums.MessageGeneralEnum;
import org.cerberus.crud.entity.TestCase;
import org.cerberus.crud.entity.TestCaseCountryProperties;
import org.cerberus.crud.entity.TestCaseExecution;
import org.cerberus.crud.entity.TestCaseExecutionData;
import org.cerberus.crud.entity.TestCaseExecutionSysVer;
import org.cerberus.crud.entity.TestCaseStep;
import org.cerberus.crud.entity.TestCaseStepAction;
import org.cerberus.crud.entity.TestCaseStepActionControl;
import org.cerberus.crud.entity.TestCaseStepActionControlExecution;
import org.cerberus.crud.entity.TestCaseStepActionExecution;
import org.cerberus.crud.entity.TestCaseStepExecution;
import org.cerberus.exception.CerberusEventException;
import org.cerberus.exception.CerberusException;
import org.cerberus.crud.factory.IFactoryTestCaseExecutionSysVer;
import org.cerberus.crud.factory.IFactoryTestCaseStepActionControlExecution;
import org.cerberus.crud.factory.IFactoryTestCaseStepActionExecution;
import org.cerberus.crud.factory.IFactoryTestCaseStepExecution;
import org.cerberus.log.MyLogger;
import org.cerberus.crud.service.ICountryEnvLinkService;
import org.cerberus.crud.service.ICountryEnvParamService;
import org.cerberus.crud.service.ILoadTestCaseService;
import org.cerberus.crud.service.ITestCaseCountryPropertiesService;
import org.cerberus.crud.service.ITestCaseExecutionService;
import org.cerberus.crud.service.ITestCaseExecutionSysVerService;
import org.cerberus.crud.service.ITestCaseExecutionwwwSumService;
import org.cerberus.crud.service.ITestCaseService;
import org.cerberus.crud.service.ITestCaseStepActionControlExecutionService;
import org.cerberus.crud.service.ITestCaseStepActionExecutionService;
import org.cerberus.crud.service.ITestCaseStepExecutionService;
import org.cerberus.engine.gwt.IActionService;
import org.cerberus.engine.gwt.IControlService;
import org.cerberus.engine.execution.IExecutionRunService;
import org.cerberus.engine.gwt.IPropertyService;
import org.cerberus.engine.execution.IRecorderService;
import org.cerberus.engine.execution.ISeleniumServerService;
import org.cerberus.util.StringUtil;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 *
 * @author bcivel
 */
@Service
public class ExecutionRunService implements IExecutionRunService {

    private static final org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(ExecutionRunService.class);

    @Autowired
    private ISeleniumServerService serverService;
    @Autowired
    private IActionService actionService;
    @Autowired
    private IPropertyService propertyService;
    @Autowired
    private IControlService controlService;
    @Autowired
    private ITestCaseService testCaseService;
    @Autowired
    private ITestCaseStepExecutionService testCaseStepExecutionService;
    @Autowired
    private ITestCaseStepActionExecutionService testCaseStepActionExecutionService;
    @Autowired
    private ITestCaseStepActionControlExecutionService testCaseStepActionControlExecutionService;
    @Autowired
    private ITestCaseExecutionService testCaseExecutionService;
    @Autowired
    private ITestCaseExecutionSysVerService testCaseExecutionSysVerService;
    @Autowired
    private ICountryEnvLinkService countryEnvLinkService;
    @Autowired
    private ITestCaseExecutionwwwSumService testCaseExecutionwwwSumService;
    @Autowired
    private ITestCaseCountryPropertiesService testCaseCountryPropertiesService;
    @Autowired
    private ICountryEnvParamService countryEnvParamService;
    @Autowired
    private ILoadTestCaseService loadTestCaseService;
    @Autowired
    private IFactoryTestCaseStepExecution factoryTestCaseStepExecution;
    @Autowired
    private IFactoryTestCaseStepActionExecution factoryTestCaseStepActionExecution;
    @Autowired
    private IFactoryTestCaseStepActionControlExecution factoryTestCaseStepActionControlExecution;
    @Autowired
    private IFactoryTestCaseExecutionSysVer factoryTestCaseExecutionSysVer;
    @Autowired
    private ExecutionUUID executionUUID;
    @Autowired
    private IRecorderService recorderService;

    @Override
    public TestCaseExecution executeTestCase(TestCaseExecution tCExecution) throws CerberusException {
        long runID = tCExecution.getId();
        String logPrefix = runID + " - ";
        /**
         * Feeding Build Rev of main Application system to
         * testcaseexecutionsysver table. Only if execution is not manual.
         */
        try {
            if (!(tCExecution.isManualURL())) {
                /**
                 * Insert SystemVersion in Database
                 */
                TestCaseExecutionSysVer myExeSysVer = null;
                try {
                    LOG.debug(logPrefix + "Registering Main System Version.");
                    myExeSysVer = factoryTestCaseExecutionSysVer.create(runID, tCExecution.getApplicationObj().getSystem(), tCExecution.getBuild(), tCExecution.getRevision());
                    testCaseExecutionSysVerService.insertTestCaseExecutionSysVer(myExeSysVer);
                } catch (CerberusException ex) {
                    LOG.error(logPrefix + ex.getMessage());
                }
                LOG.debug(logPrefix + "Main System Version Registered.");

                /**
                 * For all Linked environment, we also keep track on the
                 * build/rev information inside testcaseexecutionsysver table.
                 */
                LOG.debug(logPrefix + "Registering Linked System Version.");
                try {
                    List<CountryEnvLink> ceLink = null;
                    ceLink = countryEnvLinkService.convert(countryEnvLinkService.readByVarious(tCExecution.getApplicationObj().getSystem(), tCExecution.getCountry(), tCExecution.getEnvironment()));
                    for (CountryEnvLink myCeLink : ceLink) {
                        LOG.debug(logPrefix + "Linked environment found : " + myCeLink.getSystemLink() + myCeLink.getCountryLink() + myCeLink.getEnvironmentLink());

                        CountryEnvParam mycountEnvParam;
                        try {
                            mycountEnvParam = this.countryEnvParamService.convert(this.countryEnvParamService.readByKey(myCeLink.getSystemLink(), myCeLink.getCountryLink(), myCeLink.getEnvironmentLink()));
                            myExeSysVer = factoryTestCaseExecutionSysVer.create(runID, myCeLink.getSystemLink(), mycountEnvParam.getBuild(), mycountEnvParam.getRevision());
                            testCaseExecutionSysVerService.insertTestCaseExecutionSysVer(myExeSysVer);
                        } catch (CerberusException ex) {
                            // Referencial Integrity link between countryEnvLink and CountryEnvParam table should secure that exception to never happen.
                            LOG.error(logPrefix + ex.getMessage());
                            throw new CerberusException(ex.getMessageError());
                        }
                    }
                } catch (CerberusException ex) {
                    LOG.debug(logPrefix + "No Linked environment found.");
                }
                LOG.debug(logPrefix + "Linked System Version Registered.");
            }

            /**
             * Get used SeleniumCapabilities (empty if application is not GUI)
             */
            LOG.debug(logPrefix + "Getting Selenium capabitities for GUI applications.");
            if (tCExecution.getApplicationObj().getType().equalsIgnoreCase("GUI")) {
                try {
                    Capabilities caps = this.serverService.getUsedCapabilities(tCExecution.getSession());
                    tCExecution.setBrowserFullVersion(caps.getBrowserName() + " " + caps.getVersion() + " " + caps.getPlatform().toString());
                    tCExecution.setVersion(caps.getVersion());
                    tCExecution.setPlatform(caps.getPlatform().toString());
                } catch (Exception ex) {
                    LOG.error(logPrefix + "Exception on selenium getting Used Capabilities :" + ex.toString());
                }
                LOG.debug(logPrefix + "Selenium capabitities loaded.");
            } else {
                // If Selenium is not needed, the selenium and browser info is set to empty.
                tCExecution.setSeleniumIP("");
                tCExecution.setSeleniumPort("");
                tCExecution.setBrowser("");
                tCExecution.setVersion("");
                tCExecution.setPlatform("");
                LOG.debug(logPrefix + "No Selenium capabitities loaded because application not GUI : " + tCExecution.getApplicationObj().getType());
            }

            /**
             * Load PreTestCase information and set PreTCase to the
             * TestCaseExecution object
             */
            tCExecution.setResultMessage(new MessageGeneral(MessageGeneralEnum.EXECUTION_PE_LOADINGDETAILEDDATA));
            LOG.debug(logPrefix + "Loading Pre-testcases.");
            List<TestCase> preTests = testCaseService.findTestCaseActiveByCriteria("Pre Testing", tCExecution.getTestCaseObj().getApplication(), tCExecution.getCountry());
            tCExecution.setPreTestCaseList(preTests);
            if (!(preTests == null)) {
                LOG.debug(logPrefix + "Loaded PreTest List. " + tCExecution.getPreTestCaseList().size() + " found.");
            }
            LOG.debug(logPrefix + "Pre-testcases Loaded.");

            /**
             * Load Main TestCase with Step dependencies (Actions/Control)
             */
            LOG.debug(logPrefix + "Loading all Steps information of Main testcase.");
            List<TestCaseStep> testCaseStepList;
            testCaseStepList = this.loadTestCaseService.loadTestCaseStep(tCExecution.getTestCaseObj());
            tCExecution.getTestCaseObj().setTestCaseStep(testCaseStepList);
            LOG.debug(logPrefix + "Steps information of Main testcase Loaded : " + tCExecution.getTestCaseObj().getTestCaseStep().size() + " Step(s) found.");

            /**
             * Load Pre TestCase with Step dependencies (Actions/Control)
             */
            LOG.debug(logPrefix + "Loading all Steps information (Actions & Controls) of all Pre-testcase.");
            List<TestCaseStep> preTestCaseStepList = new ArrayList<TestCaseStep>();
            List<TestCase> preTestCase = new ArrayList<TestCase>();
            for (TestCase myTCase : tCExecution.getPreTestCaseList()) {
                myTCase.setTestCaseStep(this.loadTestCaseService.loadTestCaseStep(myTCase));
                preTestCaseStepList.addAll(myTCase.getTestCaseStep());
                preTestCase.add(myTCase);
                LOG.debug(logPrefix + "Pre testcase : " + myTCase.getTest() + "-" + myTCase.getTestCase() + " Loaded With " + myTCase.getTestCaseStep().size() + " Step(s) found.");
            }
            tCExecution.setPreTestCaseList(preTestCase);
            LOG.debug(logPrefix + "All Steps information (Actions & Controls) of all Pre-testcase Loaded.");

            /**
             * Load All properties of the testcase
             */
            LOG.debug(logPrefix + "Loading all Properties.");
            List<TestCaseCountryProperties> tcProperties = new ArrayList();
            try {
                tcProperties = testCaseCountryPropertiesService.findAllWithDependencies(tCExecution.getTest(), tCExecution.getTestCase(), tCExecution.getCountry());
                tCExecution.setTestCaseCountryPropertyList(tcProperties);
            } catch (CerberusException ex) {
                LOG.warn("Exception getting all the properties : " + ex);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(logPrefix + "All Properties Loaded. " + tcProperties.size() + " property(ies) found : " + tcProperties);
            }

            /**
             * Start Execution of the steps/Actions/controls Iterate Steps.
             * mainExecutionTestCaseStepList will contain the list of steps to
             * execute for both pretest and test. This is where we schedule the
             * execution of the steps using mainExecutionTestCaseStepList
             * object.
             */
            LOG.debug(logPrefix + "Starting the execution with step iteration.");
            List<TestCaseStep> mainExecutionTestCaseStepList;
            mainExecutionTestCaseStepList = new ArrayList<TestCaseStep>();
            mainExecutionTestCaseStepList.addAll(preTestCaseStepList);
            mainExecutionTestCaseStepList.addAll(testCaseStepList);

            /**
             * Initialize the global TestCaseExecution Data List.
             */
            // 
            tCExecution.setResultMessage(new MessageGeneral(MessageGeneralEnum.EXECUTION_PE_TESTEXECUTING));
            try {
                testCaseExecutionService.updateTCExecution(tCExecution);
            } catch (CerberusException ex) {
                Logger.getLogger(ExecutionRunService.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
            List<TestCaseExecutionData> myExecutionDataList = new ArrayList<TestCaseExecutionData>();
            tCExecution.setTestCaseExecutionDataList(myExecutionDataList);

            for (TestCaseStep testCaseStep : mainExecutionTestCaseStepList) {

                /**
                 * Start Execution of TestCaseStep
                 */
                long startStep = new Date().getTime();

                /**
                 * Create and Register TestCaseStepExecution
                 */
                TestCaseStepExecution testCaseStepExecution = factoryTestCaseStepExecution.create(
                        runID, testCaseStep.getTest(), testCaseStep.getTestCase(),
                        testCaseStep.getStep(), testCaseStep.getSort(), null,
                        startStep, 0, startStep, 0, new BigDecimal("0"), null, new MessageEvent(MessageEventEnum.STEP_PENDING), testCaseStep, tCExecution,
                        testCaseStep.getUseStep(), testCaseStep.getUseStepTest(), testCaseStep.getUseStepTestCase(), testCaseStep.getUseStepStep(), testCaseStep.getDescription());
                testCaseStepExecutionService.insertTestCaseStepExecution(testCaseStepExecution);
                testCaseStepExecution.setExecutionResultMessage(new MessageGeneral(MessageGeneralEnum.EXECUTION_PE_TESTSTARTED));

                /**
                 * Execute Step
                 */
                LOG.debug(logPrefix + "Executing step : " + testCaseStepExecution.getTest() + " - " + testCaseStepExecution.getTestCase() + " - " + testCaseStepExecution.getStep());
                testCaseStepExecution = this.executeStep(testCaseStepExecution);

                /**
                 * Updating Execution Result Message only if execution result
                 * message of the step is not PE or OK.
                 */
                if ((!(testCaseStepExecution.getExecutionResultMessage().equals(new MessageGeneral(MessageGeneralEnum.EXECUTION_PE_TESTSTARTED))))
                        && (!(testCaseStepExecution.getExecutionResultMessage().equals(new MessageGeneral(MessageGeneralEnum.EXECUTION_OK))))) {
                    tCExecution.setResultMessage(testCaseStepExecution.getExecutionResultMessage());
                }
                if (testCaseStepExecution.getStepResultMessage().equals(new MessageEvent(MessageEventEnum.STEP_PENDING))) {
                    testCaseStepExecution.setStepResultMessage(new MessageEvent(MessageEventEnum.STEP_SUCCESS));
                }

                testCaseStepExecutionService.updateTestCaseStepExecution(testCaseStepExecution);

                if (testCaseStepExecution.isStopExecution()) {
                    break;
                }

            }

            /**
             * If at that time the execution is still PE, we move it to OK. It
             * means that no issue were met.
             */
            if ((tCExecution.getResultMessage() == null) || (tCExecution.getResultMessage().equals(new MessageGeneral(MessageGeneralEnum.EXECUTION_PE_TESTSTARTED)))) {
                tCExecution.setResultMessage(new MessageGeneral(MessageGeneralEnum.EXECUTION_OK));
            }

            /**
             * We record Selenium log at the end of the execution.
             */
            try {
                recorderService.recordSeleniumLog(tCExecution);
            } catch (Exception ex) {
                LOG.error(logPrefix + "Exception Getting Selenium Logs " + tCExecution.getId() + " Exception :" + ex.toString());
            }
            /**
             * We stop the server session here (selenium for ex.).
             */
            try {
                tCExecution = this.stopTestCase(tCExecution);
            } catch (Exception ex) {
                LOG.error(logPrefix + "Exception Stopping Test " + tCExecution.getId() + " Exception :" + ex.toString());
            }

        } finally {

            try {
                executionUUID.removeExecutionUUID(tCExecution.getExecutionUUID());
                MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Clean ExecutionUUID");

            } catch (Exception ex) {
                MyLogger.log(ExecutionRunService.class.getName(), Level.FATAL, "Exception cleaning Memory: " + ex.toString());
                //TODO:FN debug messages to be removed
                org.apache.log4j.Logger.getLogger(ExecutionStartService.class.getName()).log(org.apache.log4j.Level.DEBUG,
                        "[DEBUG] Exception cleaning Memory:" + ex.getMessage());
            }

            MyLogger.log(ExecutionRunService.class.getName(), Level.INFO, "Execution Finished : UUID=" + tCExecution.getExecutionUUID()
                    + "__ID=" + tCExecution.getId() + "__RC=" + tCExecution.getControlStatus() + "__"
                    + "TestName=" + tCExecution.getEnvironment() + "." + tCExecution.getCountry() + "."
                    + tCExecution.getBuild() + "." + tCExecution.getRevision() + "." + tCExecution.getTest() + "_"
                    + tCExecution.getTestCase() + "_" + tCExecution.getTestCaseObj().getDescription().replace(".", ""));

        }
        //TODO:FN debug messages to be removed
        if (tCExecution.getControlStatus().equals("PE")) {
            org.apache.log4j.Logger.getLogger(ExecutionStartService.class.getName()).log(org.apache.log4j.Level.DEBUG,
                    "[DEBUG] EXECUTION FINISHED WITH PE ? " + "Execution Finished : UUID=" + tCExecution.getExecutionUUID()
                    + "__ID=" + tCExecution.getId() + "__RC=" + tCExecution.getControlStatus() + "__"
                    + "TestName=" + tCExecution.getEnvironment() + "." + tCExecution.getCountry() + "."
                    + tCExecution.getBuild() + "." + tCExecution.getRevision() + "." + tCExecution.getTest() + "_"
                    + tCExecution.getTestCase() + "_" + tCExecution.getTestCaseObj().getDescription().replace(".", ""));
        }
        //Notify it's finnished
//        WebsocketTest wst = new WebsocketTest();
//        try {
//            wst.handleMessage(tCExecution.getTag());
//        } catch (IOException ex) {
//            Logger.getLogger(ExecutionRunService.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        }
//                    
        return tCExecution;

    }

    @Override
    public TestCaseExecution stopTestCase(TestCaseExecution tCExecution) {

        /**
         * Stop Execution
         */
        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, tCExecution.getId() + " - Stop the execution " + tCExecution.getId() + " UUID:" + tCExecution.getExecutionUUID());
        try {
            //TODO:FN debug messages to be removed
            org.apache.log4j.Logger.getLogger(ExecutionStartService.class.getName()).log(org.apache.log4j.Level.DEBUG, "[DEBUG] STOP " + "__ID=" + tCExecution.getId());
            this.stopRunTestCase(tCExecution);
        } catch (Exception ex) {
            MyLogger.log(ExecutionRunService.class.getName(), Level.FATAL, "Exception Stopping Execution " + tCExecution.getId() + " Exception :" + ex.toString());
        }

        /**
         * Collecting and calculating Statistics.
         */
        try {
            this.collectExecutionStats(tCExecution);
        } catch (Exception ex) {
            MyLogger.log(ExecutionRunService.class.getName(), Level.FATAL, "Exception collecting stats for execution " + tCExecution.getId() + " Exception:" + ex.toString());
        }

        /**
         * Saving TestCaseExecution object.
         */
        tCExecution.setEnd(new Date().getTime());

        try {
            testCaseExecutionService.updateTCExecution(tCExecution);
        } catch (CerberusException ex) {
            MyLogger.log(ExecutionRunService.class.getName(), Level.FATAL, "Exception updating Execution :" + tCExecution.getId() + " Exception:" + ex.toString());
        }

        return tCExecution;
    }

    private TestCaseStepExecution executeStep(TestCaseStepExecution testCaseStepExecution) {

        long runID = testCaseStepExecution.getId();
        String logPrefix = runID + " - ";

        // Initialise the Step Data List.
        List<TestCaseExecutionData> myStepDataList = new ArrayList<TestCaseExecutionData>();
        testCaseStepExecution.setTestCaseExecutionDataList(myStepDataList);
        // Initialise the Data List used to enter the action.
        List<TestCaseExecutionData> myActionDataList = new ArrayList<TestCaseExecutionData>();
        /**
         * Iterate Actions
         */
        List<TestCaseStepAction> testCaseStepActionList = testCaseStepExecution.getTestCaseStep().getTestCaseStepAction();
        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Getting list of actions of the step. " + testCaseStepActionList.size() + " action(s) to perform.");

        for (TestCaseStepAction testCaseStepAction : testCaseStepActionList) {

            /**
             * Start Execution of TestCaseStepAction
             */
            long startAction = new Date().getTime();

            /**
             * Create and Register TestCaseStepActionExecution.
             */
            TestCaseStepActionExecution testCaseStepActionExecution = factoryTestCaseStepActionExecution.create(
                    testCaseStepExecution.getId(), testCaseStepAction.getTest(), testCaseStepAction.getTestCase(),
                    testCaseStepAction.getStep(), testCaseStepAction.getSequence(), testCaseStepAction.getSort(),
                    null, null, testCaseStepAction.getConditionOper(), testCaseStepAction.getConditionVal1(), testCaseStepAction.getAction(), testCaseStepAction.getValue1(), testCaseStepAction.getValue2(),
                    testCaseStepAction.getValue1(), testCaseStepAction.getValue2(), testCaseStepAction.getForceExeStatus(),
                    startAction, 0, startAction, 0, new MessageEvent(MessageEventEnum.ACTION_PENDING),
                    testCaseStepAction.getDescription(), testCaseStepAction, testCaseStepExecution);
            this.testCaseStepActionExecutionService.insertTestCaseStepActionExecution(testCaseStepActionExecution);

            /**
             * Preparing the previously calculated data coming from 1/ the other
             * steps 2/ the one current step. Attaching them to the current
             * action execution.
             */
            myActionDataList.clear();
            myActionDataList.addAll(testCaseStepExecution.gettCExecution().getTestCaseExecutionDataList());
            myActionDataList.addAll(testCaseStepExecution.getTestCaseExecutionDataList());
            testCaseStepActionExecution.setTestCaseExecutionDataList(myActionDataList);

            /**
             * CONDITION Management on Action is treated here. Checking is the
             * action can be execued here depending on the condition operator
             * and value.
             */
            boolean execute_Action = true;
            LOG.debug(logPrefix + "Starting checking the action execution condition : " + testCaseStepAction.getConditionOper());
            MessageEvent mes;
            switch (testCaseStepAction.getConditionOper()) {
                case TestCaseStepAction.CONDITIONOPER_ALWAYS:
                    execute_Action = true;
                    break;

                case TestCaseStepAction.CONDITIONOPER_IFPROPERTYEXIST:
                    if (StringUtil.isNullOrEmpty(testCaseStepAction.getConditionVal1())) {
                        mes = new MessageEvent(MessageEventEnum.ACTION_CONDITION_IFPROPERTYEXIST_MISSINGPARAMETER);
                        mes.setDescription(mes.getDescription().replace("%COND%", testCaseStepAction.getConditionOper()));
                        testCaseStepActionExecution.setActionResultMessage(mes);
                        execute_Action = false;

                    } else {
                        String myCountry = testCaseStepExecution.gettCExecution().getCountry();
                        String myProperty = testCaseStepAction.getConditionVal1();
                        execute_Action = false;
                        for (TestCaseCountryProperties prop : testCaseStepExecution.gettCExecution().getTestCaseCountryPropertyList()) {
                            LOG.debug(prop.getCountry() + " - " + myCountry + " - " + prop.getProperty() + " - " + myProperty);
                            if ((prop.getCountry().equals(myCountry)) && (prop.getProperty().equals(myProperty))) {
                                execute_Action = true;
                            }
                        }
                        if (execute_Action == false) {
                            mes = new MessageEvent(MessageEventEnum.ACTION_CONDITION_IFPROPERTYEXIST_NOTEXIST);
                            mes.setDescription(mes.getDescription().replace("%COND%", testCaseStepAction.getConditionOper()));
                            mes.setDescription(mes.getDescription().replace("%PROP%", testCaseStepAction.getConditionVal1()));
                            mes.setDescription(mes.getDescription().replace("%COUNTRY%", testCaseStepExecution.gettCExecution().getCountry()));
                            testCaseStepActionExecution.setActionResultMessage(mes);

                        }
                    }
                    break;

                case TestCaseStepAction.CONDITIONOPER_NEVER:
                    mes = new MessageEvent(MessageEventEnum.ACTION_CONDITION_NEVER);
                    mes.setDescription(mes.getDescription().replace("%COND%", testCaseStepAction.getConditionOper()));
                    testCaseStepActionExecution.setActionResultMessage(mes);
                    execute_Action = false;
                    break;

                default:
                    mes = new MessageEvent(MessageEventEnum.ACTION_CONDITION_UNKNOWN);
                    mes.setDescription(mes.getDescription().replace("%COND%", testCaseStepAction.getConditionOper()));
                    testCaseStepActionExecution.setActionResultMessage(mes);
                    execute_Action = false;
            }
            LOG.debug(logPrefix + "Finished to evaluate the execution condition : " + execute_Action);

            // Execute or not the action here.
            if (execute_Action) {
                MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Executing action : " + testCaseStepActionExecution.getAction() + " with property : " + testCaseStepActionExecution.getPropertyName());
                testCaseStepActionExecution = this.executeAction(testCaseStepActionExecution);

                /**
                 * If Action or property reported to stop the testcase, we stop
                 * it and update the step with the message.
                 */
                testCaseStepExecution.setStopExecution(testCaseStepActionExecution.isStopExecution());
                if ((!(testCaseStepActionExecution.getExecutionResultMessage().equals(new MessageGeneral(MessageGeneralEnum.EXECUTION_OK))))
                        && (!(testCaseStepActionExecution.getExecutionResultMessage().equals(new MessageGeneral(MessageGeneralEnum.EXECUTION_PE_TESTEXECUTING))))) {
                    testCaseStepExecution.setExecutionResultMessage(testCaseStepActionExecution.getExecutionResultMessage());
                    testCaseStepExecution.setStepResultMessage(testCaseStepActionExecution.getActionResultMessage());
                }
                if (testCaseStepActionExecution.isStopExecution()) {
                    break;
                }

            } else { // We don't execute the action and record a generic execution.

                /**
                 * Record Screenshot, PageSource
                 */
                recorderService.recordExecutionInformationAfterStepActionandControl(testCaseStepActionExecution, null);

                MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registering Action : " + testCaseStepActionExecution.getAction());
                this.testCaseStepActionExecutionService.updateTestCaseStepActionExecution(testCaseStepActionExecution);
                MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registered Action");

            }

            /**
             * We calculate the property.
             */
            if (false) {
                String propertyToCalculate = testCaseStepActionExecution.getValue2();
                if ((!propertyToCalculate.equals("null")) && !StringUtil.isNullOrEmpty(propertyToCalculate)) {
                    /**
                     * Only calculate property if it is feed.
                     */
                    LOG.debug(logPrefix + "Calculating property : " + propertyToCalculate);
                    String propertyToCalculate_value = "";
                    try {
                        /**
                         * Calculating the data (Property).
                         */
                        boolean isCalledFromCalculateProperty = false;
                        if (testCaseStepActionExecution.getAction().equals("calculateProperty")) {
                            isCalledFromCalculateProperty = true;
                            //TODO  check if this is ever executed
                            /*if (StringUtil.isNullOrEmpty(testCaseStepActionExecution.getObject()) && StringUtil.isNullOrEmpty(testCaseStepActionExecution.getProperty())) {
                            throw new CerberusEventException(new MessageEvent(MessageEventEnum.PROPERTY_FAILED_CALCULATE_OBJECTPROPERTYNULL));
                        }*/
                        }
                        propertyToCalculate_value = propertyService.decodeValueWithExistingProperties("%" + propertyToCalculate + "%", testCaseStepActionExecution, isCalledFromCalculateProperty);
                    } catch (CerberusEventException ex) {
                        Logger.getLogger(ExecutionRunService.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                    }
                    TestCaseExecutionData testCaseExecutionData = null;
                    // We get back here the TestCaseExecutionData from a previous calculation based on the Property name. In case Property nema is PROP(SUBDATA), we consider only PROP.
                    String masterPropertyName = propertyToCalculate.split("\\(")[0];
                    for (TestCaseExecutionData tced : testCaseStepExecution.gettCExecution().getTestCaseExecutionDataList()) {
                        if (tced.getProperty().equals(masterPropertyName)) {
                            testCaseExecutionData = tced;
                            break;
                        }
                    }
                    /**
                     * Adding the calculated data to the current step Execution
                     * and ActionExecution.
                     */
                    myStepDataList.add(testCaseExecutionData);
                    testCaseStepExecution.setTestCaseExecutionDataList(myStepDataList);
                    myActionDataList.add(testCaseExecutionData);
                    testCaseStepActionExecution.setTestCaseExecutionDataList(myActionDataList);

                    if ((testCaseExecutionData != null) && (testCaseExecutionData.getPropertyResultMessage().equals(new MessageEvent(MessageEventEnum.PROPERTY_SUCCESS)))) {
                        /**
                         * If property could be calculated, we execute the
                         * action.
                         */
                        testCaseStepActionExecution.setValue2(propertyToCalculate_value);
                        testCaseStepActionExecution.setPropertyName(propertyToCalculate);
                        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Executing action : " + testCaseStepActionExecution.getAction() + " with property : " + testCaseStepActionExecution.getPropertyName());
                        testCaseStepActionExecution = this.executeAction(testCaseStepActionExecution);
                    } else {
                        if (testCaseExecutionData == null) {
                            mes = new MessageEvent(MessageEventEnum.ACTION_NOTEXECUTED_NO_PROPERTY_DEFINITION);
                            mes.setDescription(mes.getDescription().replace("%PROP%", testCaseStepActionExecution.getValue2()));
                            mes.setDescription(mes.getDescription().replace("%COUNTRY%", testCaseStepActionExecution.getTestCaseStepExecution().gettCExecution().getCountry()));
                            testCaseStepActionExecution.setActionResultMessage(mes);

                        } else {

                            /**
                             * Any other cases (Property does not exist or
                             * failed to be calculated), we just don't execute
                             * the action and move Property Execution message to
                             * the action.
                             */
                            testCaseStepActionExecution.setStopExecution(testCaseExecutionData.isStopExecution());
                            testCaseStepActionExecution.setExecutionResultMessage(testCaseExecutionData.getExecutionResultMessage());

                            /**
                             * Register the empty Action in database.
                             */
                            if (testCaseExecutionData.getPropertyResultMessage().equals(new MessageEvent(MessageEventEnum.PROPERTY_FAILED_NO_PROPERTY_DEFINITION))) {
                                mes = new MessageEvent(MessageEventEnum.ACTION_NOTEXECUTED_NO_PROPERTY_DEFINITION);
                                mes.setDescription(mes.getDescription().replace("%PROP%", testCaseStepActionExecution.getValue2()));
                                mes.setDescription(mes.getDescription().replace("%COUNTRY%", testCaseStepActionExecution.getTestCaseStepExecution().gettCExecution().getCountry()));
                                testCaseStepActionExecution.setActionResultMessage(mes);
                            } else {
                                testCaseStepActionExecution.setActionResultMessage(new MessageEvent(MessageEventEnum.ACTION_FAILED_PROPERTYFAILED,
                                        testCaseExecutionData.getPropertyResultMessage().isGetPageSource(),
                                        testCaseExecutionData.getPropertyResultMessage().isDoScreenshot())
                                );
                            }
                        }

                        /**
                         * Record Screenshot, PageSource
                         */
                        recorderService.recordExecutionInformationAfterStepActionandControl(testCaseStepActionExecution, null);

                        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registering Action : " + testCaseStepActionExecution.getAction());
                        this.testCaseStepActionExecutionService.updateTestCaseStepActionExecution(testCaseStepActionExecution);
                        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registered Action");

                    }
                } else /**
                 * If no property defined, we just execute the action.
                 */
                 if (testCaseStepActionExecution.getAction().equals("calculateProperty") && StringUtil.isNullOrEmpty(testCaseStepActionExecution.getValue1())) {
                        //TODO check which actions can be executed without property and object
                        //if there is no object and also we are using the calculateProperty, then the step execution should be stopped 
                        mes = new MessageEvent(MessageEventEnum.PROPERTY_FAILED_CALCULATE_OBJECTPROPERTYNULL);

                        testCaseStepActionExecution.setStopExecution(mes.isStopTest());
                        testCaseStepActionExecution.setActionResultMessage(mes);
                        testCaseStepActionExecution.setExecutionResultMessage(new MessageGeneral(mes.getMessage()));
                        Logger.getLogger(ExecutionRunService.class.getName()).log(java.util.logging.Level.SEVERE, null, mes.getDescription());

                        recorderService.recordExecutionInformationAfterStepActionandControl(testCaseStepActionExecution, null);

                        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registering Action : " + testCaseStepActionExecution.getAction());
                        this.testCaseStepActionExecutionService.updateTestCaseStepActionExecution(testCaseStepActionExecution);
                        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registered Action");

                    } else {
                        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Executing action : " + testCaseStepActionExecution.getAction() + " without property.");
                        testCaseStepActionExecution = this.executeAction(testCaseStepActionExecution);
                    }

                /**
                 * If Action or property reported to stop the testcase, we stop
                 * it and update the step with the message.
                 */
                testCaseStepExecution.setStopExecution(testCaseStepActionExecution.isStopExecution());
                if ((!(testCaseStepActionExecution.getExecutionResultMessage().equals(new MessageGeneral(MessageGeneralEnum.EXECUTION_OK))))
                        && (!(testCaseStepActionExecution.getExecutionResultMessage().equals(new MessageGeneral(MessageGeneralEnum.EXECUTION_PE_TESTEXECUTING))))) {
                    testCaseStepExecution.setExecutionResultMessage(testCaseStepActionExecution.getExecutionResultMessage());
                    testCaseStepExecution.setStepResultMessage(testCaseStepActionExecution.getActionResultMessage());
                }
                if (testCaseStepActionExecution.isStopExecution()) {
                    break;
                }
            }

        }
        testCaseStepExecution.setEnd(new Date().getTime());
        this.testCaseStepExecutionService.updateTestCaseStepExecution(testCaseStepExecution);

        return testCaseStepExecution;
    }

    private TestCaseStepActionExecution executeAction(TestCaseStepActionExecution testCaseStepActionExecution) {

        testCaseStepActionExecution = this.actionService.doAction(testCaseStepActionExecution);

        /**
         * Record Screenshot, PageSource
         */
        try {
            recorderService.recordExecutionInformationAfterStepActionandControl(testCaseStepActionExecution, null);
        } catch (Exception ex) {
            MyLogger.log(ExecutionRunService.class.getName(), Level.ERROR, "Unable to record Screenshot/PageSource : " + ex.toString());
        }

        /**
         * Register Action in database
         */
        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registering Action : " + testCaseStepActionExecution.getAction());
        this.testCaseStepActionExecutionService.updateTestCaseStepActionExecution(testCaseStepActionExecution);
        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registered Action");

        if (testCaseStepActionExecution.isStopExecution()) {
            return testCaseStepActionExecution;
        }
        //As controls are associated with an action, the current state for the action is stored in order to restore it
        //if some property is not defined for the country
        MessageEvent actionMessage = testCaseStepActionExecution.getActionResultMessage();
        MessageGeneral excutionResultMessage = testCaseStepActionExecution.getExecutionResultMessage();
        /**
         * Iterate Control
         */
        List<TestCaseStepActionControl> tcsacList = testCaseStepActionExecution.getTestCaseStepAction().getTestCaseStepActionControl();
        for (TestCaseStepActionControl testCaseStepActionControl : tcsacList) {

            /**
             * Start Execution of TestCAseStepActionControl
             */
            long startControl = new Date().getTime();

            /**
             * If control linked to skipAction, then override controlType into
             * skipControl
             */
            //TODO Replace with Action and control ENUM
            if (testCaseStepActionExecution.getAction().equals("skipAction")) {
                testCaseStepActionControl.setType("skipControl");
            }

            /**
             * Create and Register TestCaseStepActionControlExecution
             */
            MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Creating TestCaseStepActionControlExecution");
            TestCaseStepActionControlExecution testCaseStepActionControlExecution
                    = factoryTestCaseStepActionControlExecution.create(testCaseStepActionExecution.getId(), testCaseStepActionControl.getTest(),
                            testCaseStepActionControl.getTestCase(), testCaseStepActionControl.getStep(), testCaseStepActionControl.getSequence(), testCaseStepActionControl.getControl(), testCaseStepActionControl.getSort(),
                            null, null, testCaseStepActionControl.getType(), testCaseStepActionControl.getControlProperty(), testCaseStepActionControl.getControlValue(),
                            testCaseStepActionControl.getFatal(), startControl, 0, 0, 0, testCaseStepActionControl.getDescription(), testCaseStepActionExecution, new MessageEvent(MessageEventEnum.CONTROL_PENDING));
            this.testCaseStepActionControlExecutionService.insertTestCaseStepActionControlExecution(testCaseStepActionControlExecution);

            MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Executing control : " + testCaseStepActionControlExecution.getControl() + " type : " + testCaseStepActionControlExecution.getControlType());
            testCaseStepActionControlExecution = executeControl(testCaseStepActionControlExecution);

            /**
             * We update the Action with the execution message and stop flag
             * from the control. We update the status only if the control is not
             * OK. This is to prevent moving the status to OK when it should
             * stay KO when a control failed previously.
             */
            testCaseStepActionExecution.setStopExecution(testCaseStepActionControlExecution.isStopExecution());
            if (!(testCaseStepActionControlExecution.getControlResultMessage().equals(new MessageEvent(MessageEventEnum.CONTROL_SUCCESS)))) {
                //NA is a special case of not having success while calculating the property; the action shouldn't be stopped
                if (testCaseStepActionControlExecution.getControlResultMessage().equals(new MessageEvent(MessageEventEnum.PROPERTY_FAILED_NO_PROPERTY_DEFINITION))) {
                    //restores the messages information if the property is not defined for the country
                    testCaseStepActionExecution.setActionResultMessage(actionMessage);
                    testCaseStepActionExecution.setExecutionResultMessage(excutionResultMessage);
                } else {
                    testCaseStepActionExecution.setExecutionResultMessage(testCaseStepActionControlExecution.getExecutionResultMessage());
                    testCaseStepActionExecution.setActionResultMessage(testCaseStepActionControlExecution.getControlResultMessage());
                }
            }
            /**
             * If Control reported to stop the testcase, we stop it.
             */
            if (testCaseStepActionControlExecution.isStopExecution()) {
                break;
            }

        }

        return testCaseStepActionExecution;

    }

    private TestCaseStepActionControlExecution executeControl(TestCaseStepActionControlExecution testCaseStepActionControlExecution) {

        testCaseStepActionControlExecution = this.controlService.doControl(testCaseStepActionControlExecution);

        /**
         * Record Screenshot, PageSource
         */
        recorderService.recordExecutionInformationAfterStepActionandControl(testCaseStepActionControlExecution.getTestCaseStepActionExecution(), testCaseStepActionControlExecution);

        /**
         * Register Control in database
         */
        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registering Control : " + testCaseStepActionControlExecution.getControl());
        this.testCaseStepActionControlExecutionService.updateTestCaseStepActionControlExecution(testCaseStepActionControlExecution);
        MyLogger.log(ExecutionRunService.class.getName(), Level.DEBUG, "Registered Control");

        return testCaseStepActionControlExecution;
    }

    private TestCaseExecution stopRunTestCase(TestCaseExecution tCExecution) {
        if (tCExecution.getApplicationObj().getType().equalsIgnoreCase("GUI")
                || tCExecution.getApplicationObj().getType().equalsIgnoreCase("APK")
                || tCExecution.getApplicationObj().getType().equalsIgnoreCase("IPA")) {
            try {
                this.serverService.stopServer(tCExecution.getSession());
                //TODO:FN debug messages to be removed
                org.apache.log4j.Logger.getLogger(ExecutionStartService.class.getName()).log(org.apache.log4j.Level.DEBUG,
                        "[DEBUG] STOP SERVER " + "__ID=" + tCExecution.getId());
            } catch (UnreachableBrowserException exception) {
                //TODO:FN debug messages to be removed
                org.apache.log4j.Logger.getLogger(ExecutionStartService.class.getName()).log(org.apache.log4j.Level.DEBUG,
                        "[DEBUG] FAILED TO STOP " + "__ID=" + tCExecution.getId() + " " + exception.toString());
                MyLogger.log(ExecutionRunService.class.getName(), Level.FATAL, "Selenium didn't manage to close browser - " + exception.toString());
            }
        }
        return tCExecution;
    }

    private TestCaseExecution collectExecutionStats(TestCaseExecution tCExecution) {
        if (tCExecution.getVerbose() > 0) {
            this.testCaseExecutionwwwSumService.registerSummary(tCExecution.getId());
        }
        return tCExecution;
    }

    @Override
    @Async
    public TestCaseExecution executeAsynchroneouslyTestCase(TestCaseExecution tCExecution) throws CerberusException {
        try {
            return executeTestCase(tCExecution);
        } catch (CerberusException ex) {
            throw new CerberusException(ex.getMessageError());
        }
    }

}
