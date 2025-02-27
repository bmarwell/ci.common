/**
 * (C) Copyright IBM Corporation 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */package io.openliberty.tools.common.plugins.util;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.openliberty.tools.common.CommonLoggerI;

public class VariableUtility {
    private static final String VARIABLE_NAME_PATTERN = "\\$\\{(.*?)\\}";
    private static final Pattern varNamePattern = Pattern.compile(VARIABLE_NAME_PATTERN);

    /*
     * Attempts to resolve all variables in the passed in nodeValue. Variable value/defaultValue can reference other variables.
     * This method is called recursively to resolve the variables. The variableChain collection keeps track of the variable references
     * in a resolution chain in order to prevent an infinite loop. The variableChain collection should be passed as null on the initial call.
     */
    public static String resolveVariables(CommonLoggerI log, String nodeValue, Collection<String> variableChain, 
                                            Properties props, Properties defaultProps, Map<String, File> libDirPropFiles) {

        // For Windows, avoid escaping the backslashes in the resolvedValue by changing to forward slashes
        String resolved = nodeValue.replace("\\","/");
        Matcher varNameMatcher = varNamePattern.matcher(nodeValue);

        Collection<String> variablesToResolve = new HashSet<String> ();

        while (varNameMatcher.find()) {
            String varName = varNameMatcher.group(1);
            if (variableChain != null && variableChain.contains(varName)) {
                // Found recursive reference when resolving variables. Log message and return null.
                log.debug("Found a recursive variable reference when resolving ${" + varName + "}");
                return null;
            } else {
                variablesToResolve.add(varName);
            }
        }

        for (String nextVariable : variablesToResolve) {
            String value = getPropertyValue(nextVariable, props, defaultProps, libDirPropFiles);

            if (value != null && !value.isEmpty()) {
                Collection<String> thisVariableChain = new HashSet<String> ();
                thisVariableChain.add(nextVariable);

                if (variableChain != null && !variableChain.isEmpty()) {
                    thisVariableChain.addAll(variableChain);
                }

                String resolvedValue = resolveVariables(log, value, thisVariableChain, props, defaultProps, libDirPropFiles);

                if (resolvedValue != null) {
                    String escapedVariable = Matcher.quoteReplacement(nextVariable);
                    // For Windows, avoid escaping the backslashes in the resolvedValue by changing to forward slashes
                    resolvedValue = resolvedValue.replace("\\","/");
                    resolved = resolved.replaceAll("\\$\\{" + escapedVariable + "\\}", resolvedValue);
                } else {
                    // Variable value could not be resolved. Log message and return null.
                    log.debug("Could not resolve the value " + value + " for variable ${" + nextVariable + "}");
                    return null;
                }
            } else {
                // Variable could not be resolved. Log message and return null.
                log.debug("Variable " + nextVariable + " cannot be resolved.");
                return null;
            }
        }

        log.debug("Expression "+ nodeValue +" evaluated and replaced with "+resolved);

        return resolved;
    }

    public static String getPropertyValue(String propertyName, Properties props, Properties defaultProps, Map<String, File> libDirPropFiles) {
        String value = null;
        if(!libDirPropFiles.containsKey(propertyName)) {
            value = props.getProperty(propertyName);
            if (value == null) {
                // Check for default value since no other value found.
                value = defaultProps.getProperty(propertyName);
            }
            if (value == null && propertyName.startsWith("env.") && propertyName.length() > 4) {
                // Look for property without the 'env.' prefix
                String newPropName = propertyName.substring(4);
                value = props.getProperty(newPropName);
                if (value == null) {
                    // Check for default value since no other value found.
                    value = defaultProps.getProperty(newPropName);
                }
            }
        } else {
            File envDirectory = libDirPropFiles.get(propertyName);
            value = envDirectory.toString();
        }  
        
        if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
            // need to remove beginning/ending quotes
            if (value.length() > 2) {
                value = value.substring(1, value.length() -1);
            }
        }
        return value;
    }
    
}
