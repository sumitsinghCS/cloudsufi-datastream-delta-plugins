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

package io.cdap.plugin.utils;

import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.TableResult;
import io.cdap.e2e.utils.BigQueryClient;
import io.cdap.e2e.utils.PluginPropertyUtils;
import org.junit.Assert;
import stepsdesign.BeforeActions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
/**
 * Contains bq helper methods used in e2e tests.
 */
public class BigQuery {

    public static List<Map<String, Object>> getBigQueryRecordsAsMap(String projectId, String database, String tableName)
            throws IOException, InterruptedException {
        String query = "SELECT * EXCEPT ( _row_id, _source_timestamp, _sort) FROM `" + projectId
                + "." + database + "." + tableName + "`";
        List<Map<String, Object>> bqRecords = new ArrayList<>();
        TableResult results = BigQueryClient.getQueryResult(query);
        List<String> columns = new ArrayList<>();
        for (Field field : results.getSchema().getFields()) {
            columns.add(field.getName() + "#" + field.getType());
        }
        for (FieldValueList row : results.getValues()) {
            Map<String, Object> record = new HashMap<>();
            int index = 0;
            for (FieldValue fieldValue : row) {
                String columnName = columns.get(index).split("#")[0];
                String dataType = columns.get(index).split("#")[1];
                Object value;
                if (dataType.equalsIgnoreCase("TIMESTAMP")) {
                    value = fieldValue.getTimestampValue();
                } else {
                    value = fieldValue.getValue();
                    value = value != null ? value.toString() : null;
                }
                record.put(columnName, value);
                index++;
            }
            bqRecords.add(record);
        }
        return bqRecords;
    }
    public static void waitForFlush() throws InterruptedException {
        int flushInterval = Integer.parseInt(PluginPropertyUtils.pluginProp("loadInterval"));
        TimeUnit time = TimeUnit.SECONDS;
        time.sleep(2 * flushInterval + 60);
    }

    public static void deleteTable(String tableName) throws IOException, InterruptedException {
        try {
            BigQueryClient.dropBqQuery(tableName);
            BeforeActions.scenario.write("BQ Target table - " + tableName + " deleted successfully");
        } catch (BigQueryException e) {
            if (e.getMessage().contains("Not found: Table")) {
                BeforeActions.scenario.write("BQ Target Table does not exist");
            }
            Assert.fail(e.getMessage());
        }
    }
}
