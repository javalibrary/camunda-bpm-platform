/*
 * Copyright © 2012 - 2018 camunda services GmbH and various authors (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.util;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.exception.NotValidException;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.bpmn.parser.FailedJobRetryConfiguration;
import org.camunda.bpm.engine.impl.calendar.DurationHelper;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.el.Expression;
import org.camunda.bpm.engine.impl.el.ExpressionManager;

public class ParseUtil {

  private static final EngineUtilLogger LOG = ProcessEngineLogger.UTIL_LOGGER;

  protected static final Pattern REGEX_TTL_ISO = Pattern.compile("^P(\\d+)D$");

  /**
   * Parse History Time To Live in ISO-8601 format to integer and set into the given entity
   * @param historyTimeToLive
   */
  public static Integer parseHistoryTimeToLive(String historyTimeToLive) {
    Integer timeToLive = null;

    if (historyTimeToLive != null && !historyTimeToLive.isEmpty()) {
      Matcher matISO = REGEX_TTL_ISO.matcher(historyTimeToLive);
      if (matISO.find()) {
        historyTimeToLive = matISO.group(1);
      }
      timeToLive = parseIntegerAttribute("historyTimeToLive", historyTimeToLive);
    }

    if (timeToLive != null && timeToLive < 0) {
      throw new NotValidException("Cannot parse historyTimeToLive: negative value is not allowed");
    }

    return timeToLive;
  }

  protected static Integer parseIntegerAttribute(String attributeName, String text) {
    Integer result = null;

    if (text != null && !text.isEmpty()) {
      try {
        result = Integer.parseInt(text);
      }
      catch (NumberFormatException e) {
        throw new ProcessEngineException("Cannot parse " + attributeName + ": " + e.getMessage());
      }
    }

    return result;
  }

  public static FailedJobRetryConfiguration parseRetryIntervals(String retryIntervals) {

    if (retryIntervals != null && !retryIntervals.isEmpty()) {

      if (StringUtil.isExpression(retryIntervals)) {
        ExpressionManager expressionManager = Context.getProcessEngineConfiguration().getExpressionManager();
        Expression expression = expressionManager.createExpression(retryIntervals);
        return new FailedJobRetryConfiguration(expression);
      }

      String[] intervals = StringUtil.split(retryIntervals, ",");
      int retries = intervals.length + 1;

      if (intervals.length == 1) {
        try {
          DurationHelper durationHelper = new DurationHelper(intervals[0]);

          if (durationHelper.isRepeat()) {
            retries = durationHelper.getTimes();
          }
        } catch (Exception e) {
          LOG.logParsingRetryIntervals(intervals[0], e);
          return null;
        }
      }
      return new FailedJobRetryConfiguration(retries, Arrays.asList(intervals));
    } else {
      return null;
    }
  }
}
