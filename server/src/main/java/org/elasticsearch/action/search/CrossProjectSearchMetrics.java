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
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.transport.RemoteClusterAware;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.ToXContentFragment;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public class CrossProjectSearchMetrics implements Writeable, ToXContentFragment {
    private long planningPhaseTookTime;
    private long mergingPhaseTookTime;
    private final Map<String, Long> projectsTookTime;

    public static final ParseField PLANNING_PHASE_TOOK_TIME_FIELD = new ParseField("planning_phase_took_time");
    public static final ParseField MERGING_PHASE_TOOK_TIME_FIELD = new ParseField("merging_phase_took_time");
    public static final String PROJECTS_TOOK_TIME_NAME = "projects_took_time";
    public static final String PROJECTS_NAME = "projects";

    public CrossProjectSearchMetrics() {
        this.planningPhaseTookTime = 0L;
        this.mergingPhaseTookTime = 0L;
        this.projectsTookTime = new HashMap<>();
    }

    public CrossProjectSearchMetrics(StreamInput in) throws IOException {
        this.planningPhaseTookTime = in.readLong();
        this.projectsTookTime = in.readMap(StreamInput::readLong);
        this.mergingPhaseTookTime = in.readLong();
    }

    public void trackPlanningPhaseTookTime(long planningPhaseTookTime) {
        this.planningPhaseTookTime = planningPhaseTookTime;
    }

    public void trackProjectTookTime(String projectName, long projectTookTime) {
        if (projectName.equals(RemoteClusterAware.LOCAL_CLUSTER_GROUP_KEY)) {
            projectName = "origin_project";
        }

        this.projectsTookTime.put(projectName, projectTookTime);
    }

    public void trackMergingPhaseTookTime(long mergingPhaseTookTime) {
        this.mergingPhaseTookTime = mergingPhaseTookTime;
    }

    public long getPlanningPhaseTookTime() {
        return planningPhaseTookTime;
    }

    public Map<String, Long> getProjectsTookTime() {
        return projectsTookTime;
    }

    public long getMergingPhaseTookTime() {
        return mergingPhaseTookTime;
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
            builder.field(projectName, projectTookTime);
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

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeLong(planningPhaseTookTime);
        out.writeMap(projectsTookTime, StreamOutput::writeLong);
        out.writeLong(mergingPhaseTookTime);
    }
}
