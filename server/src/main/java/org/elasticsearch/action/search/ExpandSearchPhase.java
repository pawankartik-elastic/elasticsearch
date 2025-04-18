/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.action.search;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.util.Maps;
import org.elasticsearch.common.util.concurrent.AtomicArray;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.SearchPhaseResult;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.collapse.CollapseBuilder;

import java.util.Iterator;
import java.util.List;

/**
 * This search phase is an optional phase that will be executed once all hits are fetched from the shards that executes
 * field-collapsing on the inner hits. This phase only executes if field collapsing is requested in the search request and otherwise
 * forwards to the next phase immediately.
 */
class ExpandSearchPhase extends SearchPhase {

    static final String NAME = "expand";

    private final AbstractSearchAsyncAction<?> context;
    private final SearchResponseSections searchResponseSections;
    private final AtomicArray<SearchPhaseResult> queryPhaseResults;

    ExpandSearchPhase(
        AbstractSearchAsyncAction<?> context,
        SearchResponseSections searchResponseSections,
        AtomicArray<SearchPhaseResult> queryPhaseResults
    ) {
        super(NAME);
        this.context = context;
        this.searchResponseSections = searchResponseSections;
        this.queryPhaseResults = queryPhaseResults;
    }

    // protected for tests
    protected SearchPhase nextPhase() {
        return new FetchLookupFieldsPhase(context, searchResponseSections, queryPhaseResults);
    }

    /**
     * Returns <code>true</code> iff the search request has inner hits and needs field collapsing
     */
    private boolean isCollapseRequest() {
        final var searchSource = context.getRequest().source();
        return searchSource != null && searchSource.collapse() != null && searchSource.collapse().getInnerHits().isEmpty() == false;
    }

    @Override
    protected void run() {
        var searchHits = searchResponseSections.hits();
        if (isCollapseRequest() == false || searchHits.getHits().length == 0) {
            onPhaseDone();
        } else {
            doRun(searchHits);
        }
    }

    private void doRun(SearchHits searchHits) {
        SearchRequest searchRequest = context.getRequest();
        CollapseBuilder collapseBuilder = searchRequest.source().collapse();
        final List<InnerHitBuilder> innerHitBuilders = collapseBuilder.getInnerHits();
        MultiSearchRequest multiRequest = new MultiSearchRequest();
        if (collapseBuilder.getMaxConcurrentGroupRequests() > 0) {
            multiRequest.maxConcurrentSearchRequests(collapseBuilder.getMaxConcurrentGroupRequests());
        }
        for (SearchHit hit : searchHits.getHits()) {
            BoolQueryBuilder groupQuery = new BoolQueryBuilder();
            Object collapseValue = hit.field(collapseBuilder.getField()).getValue();
            if (collapseValue != null) {
                groupQuery.filter(QueryBuilders.matchQuery(collapseBuilder.getField(), collapseValue));
            } else {
                groupQuery.mustNot(QueryBuilders.existsQuery(collapseBuilder.getField()));
            }
            QueryBuilder origQuery = searchRequest.source().query();
            if (origQuery != null) {
                groupQuery.must(origQuery);
            }
            for (InnerHitBuilder innerHitBuilder : innerHitBuilders) {
                CollapseBuilder innerCollapseBuilder = innerHitBuilder.getInnerCollapseBuilder();
                SearchSourceBuilder sourceBuilder = buildExpandSearchSourceBuilder(innerHitBuilder, innerCollapseBuilder).query(groupQuery)
                    .postFilter(searchRequest.source().postFilter())
                    .runtimeMappings(searchRequest.source().runtimeMappings())
                    .pointInTimeBuilder(searchRequest.source().pointInTimeBuilder());
                SearchRequest groupRequest = new SearchRequest(searchRequest);
                if (searchRequest.pointInTimeBuilder() != null) {
                    // if the original request has a point in time, we propagate it to the inner search request
                    // and clear the indices and preference from the inner search request
                    groupRequest.indices(Strings.EMPTY_ARRAY);
                    groupRequest.preference(null);
                }
                groupRequest.source(sourceBuilder);
                multiRequest.add(groupRequest);
            }
        }
        context.getSearchTransport().sendExecuteMultiSearch(multiRequest, context.getTask(), ActionListener.wrap(response -> {
            Iterator<MultiSearchResponse.Item> it = response.iterator();
            for (SearchHit hit : searchHits.getHits()) {
                for (InnerHitBuilder innerHitBuilder : innerHitBuilders) {
                    MultiSearchResponse.Item item = it.next();
                    if (item.isFailure()) {
                        phaseFailure(item.getFailure());
                        return;
                    }
                    SearchHits innerHits = item.getResponse().getHits();
                    if (hit.getInnerHits() == null) {
                        hit.setInnerHits(Maps.newMapWithExpectedSize(innerHitBuilders.size()));
                    }
                    if (hit.isPooled() == false) {
                        // TODO: make this work pooled by forcing the hit itself to become pooled as needed here
                        innerHits = innerHits.asUnpooled();
                    }
                    hit.getInnerHits().put(innerHitBuilder.getName(), innerHits);
                    assert innerHits.isPooled() == false || hit.isPooled() : "pooled inner hits can only be added to a pooled hit";
                    innerHits.mustIncRef();
                }
            }
            onPhaseDone();
        }, this::phaseFailure));
    }

    private void phaseFailure(Exception ex) {
        context.onPhaseFailure(NAME, "failed to expand hits", ex);
    }

    private static SearchSourceBuilder buildExpandSearchSourceBuilder(InnerHitBuilder options, CollapseBuilder innerCollapseBuilder) {
        SearchSourceBuilder groupSource = new SearchSourceBuilder();
        groupSource.from(options.getFrom());
        groupSource.size(options.getSize());
        if (options.getSorts() != null) {
            options.getSorts().forEach(groupSource::sort);
        }
        if (options.getFetchSourceContext() != null) {
            if (options.getFetchSourceContext().includes().length == 0 && options.getFetchSourceContext().excludes().length == 0) {
                groupSource.fetchSource(options.getFetchSourceContext().fetchSource());
            } else {
                groupSource.fetchSource(options.getFetchSourceContext().includes(), options.getFetchSourceContext().excludes());
            }
        }
        if (options.getFetchFields() != null) {
            options.getFetchFields().forEach(groupSource::fetchField);
        }
        if (options.getDocValueFields() != null) {
            options.getDocValueFields().forEach(ff -> groupSource.docValueField(ff.field, ff.format));
        }
        if (options.getStoredFieldsContext() != null && options.getStoredFieldsContext().fieldNames() != null) {
            options.getStoredFieldsContext().fieldNames().forEach(groupSource::storedField);
        }
        if (options.getScriptFields() != null) {
            for (SearchSourceBuilder.ScriptField field : options.getScriptFields()) {
                groupSource.scriptField(field.fieldName(), field.script());
            }
        }
        if (options.getHighlightBuilder() != null) {
            groupSource.highlighter(options.getHighlightBuilder());
        }
        groupSource.explain(options.isExplain());
        groupSource.trackScores(options.isTrackScores());
        groupSource.version(options.isVersion());
        groupSource.seqNoAndPrimaryTerm(options.isSeqNoAndPrimaryTerm());
        if (innerCollapseBuilder != null) {
            groupSource.collapse(innerCollapseBuilder);
        }
        return groupSource;
    }

    private void onPhaseDone() {
        context.executeNextPhase(NAME, this::nextPhase);
    }
}
