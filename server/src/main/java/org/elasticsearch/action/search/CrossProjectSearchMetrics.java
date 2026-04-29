/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.action.search;

import org.elasticsearch.common.Strings;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.ToXContentFragment;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeSet;

class CrossProjectSearchMetrics implements ToXContentFragment {
    private long planningPhaseTookTime;
    private long mergingPhaseTookTime;
    private final HashMap<String, Long> projectsTookTime;

    public static final ParseField PLANNING_PHASE_TOOK_TIME_FIELD = new ParseField("planning_phase_took_time");
    public static final ParseField MERGING_PHASE_TOOK_TIME_FIELD = new ParseField("merging_phase_took_time");
    public static final String PROJECTS_TOOK_TIME_NAME = "projects_took_time";
    public static final String PROJECTS_NAME = "projects";
    public static final String OVERALL_TOOK_TIME_NAME = "overall_took_time";

    CrossProjectSearchMetrics() {
        this.planningPhaseTookTime = 0L;
        this.mergingPhaseTookTime = 0L;
        this.projectsTookTime = new HashMap<>();
    }

    void trackPlanningPhaseTookTime(long planningPhaseTookTime) {
        this.planningPhaseTookTime = planningPhaseTookTime;
    }

    void trackProjectTookTime(String projectName, long projectTookTime) {
        this.projectsTookTime.put(projectName, projectTookTime);
    }

    void trackMergingPhaseTookTime(long mergingPhaseTookTime) {
        this.mergingPhaseTookTime = mergingPhaseTookTime;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.field(PLANNING_PHASE_TOOK_TIME_FIELD.getPreferredName(), planningPhaseTookTime);
        builder.field(MERGING_PHASE_TOOK_TIME_FIELD.getPreferredName(), mergingPhaseTookTime);

        builder.startObject(PROJECTS_TOOK_TIME_NAME).startArray(PROJECTS_NAME);

        TreeSet<String> sorted = new TreeSet<>(projectsTookTime.keySet());
        for (String projectName : sorted) {
            long projectTookTime = projectsTookTime.get(projectName);

            builder.startObject();
            builder.field(OVERALL_TOOK_TIME_NAME, projectTookTime);
            builder.endObject();
        }

        builder.endArray().endObject();
        return builder;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CrossProjectSearchMetrics other
            && other.planningPhaseTookTime == this.planningPhaseTookTime
            && other.mergingPhaseTookTime == this.mergingPhaseTookTime
            && other.projectsTookTime.equals(this.projectsTookTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planningPhaseTookTime, mergingPhaseTookTime, projectsTookTime);
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }
}
