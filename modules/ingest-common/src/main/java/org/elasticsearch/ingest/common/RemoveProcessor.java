/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.ingest.common;

import org.elasticsearch.cluster.metadata.ProjectId;
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.ConfigurationUtils;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.script.TemplateScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.ingest.ConfigurationUtils.newConfigurationException;

/**
 * Processor that removes existing fields. Nothing happens if the field is not present.
 */
public final class RemoveProcessor extends AbstractProcessor {

    public static final String TYPE = "remove";

    private final List<TemplateScript.Factory> fieldsToRemove;
    private final List<TemplateScript.Factory> fieldsToKeep;
    private final boolean ignoreMissing;

    RemoveProcessor(
        String tag,
        String description,
        List<TemplateScript.Factory> fieldsToRemove,
        List<TemplateScript.Factory> fieldsToKeep,
        boolean ignoreMissing
    ) {
        super(tag, description);
        this.fieldsToRemove = List.copyOf(fieldsToRemove);
        this.fieldsToKeep = List.copyOf(fieldsToKeep);
        this.ignoreMissing = ignoreMissing;
    }

    @Override
    public IngestDocument execute(IngestDocument document) {
        if (fieldsToKeep.isEmpty() == false) {
            fieldsToKeepProcessor(document);
        } else {
            fieldsToRemoveProcessor(document);
        }

        return document;
    }

    private void fieldsToRemoveProcessor(IngestDocument document) {
        // micro-optimization note: actual for-each loop here rather than a .forEach because it happens to be ~5% faster in benchmarks
        for (TemplateScript.Factory field : fieldsToRemove) {
            document.removeField(document.renderTemplate(field), ignoreMissing);
        }
    }

    private void fieldsToKeepProcessor(IngestDocument document) {
        IngestDocument.getAllFields(document.getSourceAndMetadata())
            .stream()
            .filter(documentField -> IngestDocument.Metadata.isMetadata(documentField) == false)
            .filter(documentField -> shouldKeep(documentField, fieldsToKeep, document) == false)
            .forEach(documentField -> document.removeField(documentField, true));
    }

    static boolean shouldKeep(String documentField, List<TemplateScript.Factory> fieldsToKeep, IngestDocument document) {
        return fieldsToKeep.stream().anyMatch(fieldToKeep -> {
            String path = document.renderTemplate(fieldToKeep);
            return documentField.equals(path) || path.startsWith(documentField + ".") || documentField.startsWith(path + ".");
        });
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {

        private final ScriptService scriptService;

        public Factory(ScriptService scriptService) {
            this.scriptService = scriptService;
        }

        @Override
        public RemoveProcessor create(
            Map<String, Processor.Factory> registry,
            String tag,
            String description,
            Map<String, Object> config,
            ProjectId projectId
        ) throws Exception {
            final List<TemplateScript.Factory> compiledTemplatesToRemove = getTemplates(tag, config, "field");
            final List<TemplateScript.Factory> compiledTemplatesToKeep = getTemplates(tag, config, "keep");

            if (compiledTemplatesToRemove.isEmpty() && compiledTemplatesToKeep.isEmpty()) {
                throw newConfigurationException(TYPE, tag, "keep", "or [field] must be specified");
            }

            if (compiledTemplatesToRemove.isEmpty() == false && compiledTemplatesToKeep.isEmpty() == false) {
                throw newConfigurationException(TYPE, tag, "keep", "and [field] cannot both be used in the same processor");
            }

            boolean ignoreMissing = ConfigurationUtils.readBooleanProperty(TYPE, tag, config, "ignore_missing", false);
            return new RemoveProcessor(tag, description, compiledTemplatesToRemove, compiledTemplatesToKeep, ignoreMissing);
        }

        private List<TemplateScript.Factory> getTemplates(String processorTag, Map<String, Object> config, String propertyName) {
            return getFields(processorTag, config, propertyName).stream()
                .map(f -> ConfigurationUtils.compileTemplate(TYPE, processorTag, propertyName, f, scriptService))
                .toList();
        }

        private static List<String> getFields(String processorTag, Map<String, Object> config, String propertyName) {
            final List<String> fields = new ArrayList<>();

            if (config.containsKey(propertyName) == false) {
                return fields;
            }

            final Object field = ConfigurationUtils.readObject(TYPE, processorTag, config, propertyName);
            if (field instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> stringList = (List<String>) field;
                fields.addAll(stringList);
            } else {
                fields.add((String) field);
            }

            return fields;
        }
    }

    public List<TemplateScript.Factory> getFieldsToRemove() {
        return fieldsToRemove;
    }

    public List<TemplateScript.Factory> getFieldsToKeep() {
        return fieldsToKeep;
    }

}
