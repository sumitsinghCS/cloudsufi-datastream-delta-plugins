/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.plugin.actions;

import io.cdap.e2e.pages.actions.CdfPipelineRunAction;
import io.cdap.e2e.pages.locators.CdfPipelineRunLocators;
import io.cdap.e2e.utils.AssertionHelper;
import io.cdap.e2e.utils.ConstantsUtil;
import io.cdap.e2e.utils.ElementHelper;
import io.cdap.e2e.utils.PageHelper;
import io.cdap.e2e.utils.PluginPropertyUtils;
import io.cdap.e2e.utils.SeleniumDriver;
import io.cdap.e2e.utils.SeleniumHelper;
import io.cdap.e2e.utils.WaitHelper;
import io.cdap.plugin.locators.ReplicationLocators;
import io.cdap.plugin.utils.BigQuery;
import io.cdap.plugin.utils.OracleClient;
import io.cdap.plugin.utils.ValidationHelper;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import stepsdesign.BeforeActions;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Replication oracle Actions.
 */
public class ReplicationActions {
    private static String parentWindow = StringUtils.EMPTY;
    private static final String projectId = PluginPropertyUtils.pluginProp("projectId");
    private static final String database = PluginPropertyUtils.pluginProp("dataset");
    public static String tableName = PluginPropertyUtils.pluginProp("sourceTable");
    public static String schemaName = PluginPropertyUtils.pluginProp("schema");
    public static String datatypeValues = PluginPropertyUtils.pluginProp("datatypeValuesForInsertOperation");
    public static String deleteCondition = PluginPropertyUtils.pluginProp("deleteRowCondition");
    public static String updateCondition = PluginPropertyUtils.pluginProp("updateRowCondition");
    public static String updatedValue = PluginPropertyUtils.pluginProp("updatedRow");

    static {
        SeleniumHelper.getPropertiesLocators(ReplicationLocators.class);
    }
    public static void clickNextButton() throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        ElementHelper.clickOnElement(ReplicationLocators.next);
    }

    public static void clickOnOraclePlugin() {
        ElementHelper.clickOnElement(ReplicationLocators.oraclePlugin);
    }

    public static void selectTable() {
        String table = schemaName + "." + tableName;
        WaitHelper.waitForElementToBeDisplayed(ReplicationLocators.selectTable(table), 300);
        AssertionHelper.verifyElementDisplayed(ReplicationLocators.selectTable(table));
        ElementHelper.clickOnElement(ReplicationLocators.selectTable(table));
    }

    public static void deployPipeline() {
        ElementHelper.clickOnElement(ReplicationLocators.deployPipeline);
    }

    public static void startPipeline() {
        ElementHelper.clickIfDisplayed(ReplicationLocators.start, ConstantsUtil.DEFAULT_TIMEOUT_SECONDS);
    }

    public static void runThePipeline() {
        startPipeline();
        WaitHelper.waitForElementToBeDisplayed(ReplicationLocators.running);
    }

    public static void openAdvanceLogs() {
        ReplicationLocators.logs.click();
        parentWindow = SeleniumDriver.getDriver().getWindowHandle();
        ArrayList<String> tabs = new ArrayList(SeleniumDriver.getDriver().getWindowHandles());
        SeleniumDriver.getDriver().switchTo().window(tabs.get(tabs.indexOf(parentWindow) + 1));
        ReplicationLocators.advancedLogs.click();
    }

    public static void captureRawLog() {
        try {
            String rawLogs = getRawLogs();
            String logsSeparatorMessage = ConstantsUtil.LOGS_SEPARATOR_MESSAGE
                    .replace("MESSAGE", "DEPLOYED PIPELINE RUNTIME LOGS");
            BeforeActions.scenario.write(rawLogs);
            CdfPipelineRunAction.writeRawLogsToFile(BeforeActions.file, logsSeparatorMessage, rawLogs);
        } catch (Exception e) {
            BeforeActions.scenario.write("Exception in capturing logs : " + e);
        }
    }

    public static String getRawLogs() {
        CdfPipelineRunAction.viewRawLogs();
        ArrayList<String> tabs = new ArrayList(SeleniumDriver.getDriver().getWindowHandles());
        PageHelper.switchToWindow(tabs.indexOf(parentWindow) + 2);
        String logs = CdfPipelineRunLocators.logsTextbox.getText();
        Assert.assertNotNull(logs);
        PageHelper.closeCurrentWindow();
        return logs;
    }

    public static void waitTillPipelineIsRunningAndCheckForErrors() throws InterruptedException {
        //wait for datastream to startup
        int defaultTimeout = Integer.parseInt(PluginPropertyUtils.pluginProp("pipeline-initialization"));
        TimeUnit.SECONDS.sleep(defaultTimeout);
        BigQuery.waitForFlush();
        // Checking if an error message is displayed.
        Assert.assertFalse(ElementHelper.isElementDisplayed(ReplicationLocators.error));
    }

    public static void closeTheLogsAndClickOnStopButton() {
        SeleniumDriver.getDriver().switchTo().window(parentWindow);
        //Stopping the pipeline
        ElementHelper.clickOnElement(ReplicationLocators.stop);
    }

    public static void verifyTargetBigQueryRecordMatchesExpectedOracleRecord()
            throws IOException, InterruptedException, SQLException, ClassNotFoundException {
        // Checking if an error message is displayed.
        Assert.assertFalse(ElementHelper.isElementDisplayed(ReplicationLocators.error));

        List<Map<String, Object>> sourceOracleRecords = OracleClient.getOracleRecordsAsMap(tableName, schemaName);
        List<Map<String, Object>> targetBigQueryRecords =
                BigQuery.getBigQueryRecordsAsMap(projectId, database, tableName);
        ValidationHelper.validateRecords(sourceOracleRecords, targetBigQueryRecords);
    }

    public static void executeCdcEventsOnSourceTable()
            throws SQLException, ClassNotFoundException {
        OracleClient.insertRow(tableName, schemaName, datatypeValues);
        OracleClient.updateRow(tableName, schemaName, updateCondition, updatedValue);
        OracleClient.deleteRow(tableName, schemaName, deleteCondition);
        OracleClient.forceFlushCDC();
    }

    public static void waitForReplication() throws InterruptedException {
        BigQuery.waitForFlush();
    }
}
