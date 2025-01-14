/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.rest;

import org.apache.lucene.util.SetOnce;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.test.rest.FakeRestRequest;
import org.elasticsearch.test.rest.RestActionTestCase;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.core.inference.action.InferenceAction;
import org.junit.After;
import org.junit.Before;

import static org.elasticsearch.xpack.inference.rest.BaseInferenceActionTests.createResponse;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

public class RestStreamInferenceActionTests extends RestActionTestCase {
    private final SetOnce<ThreadPool> threadPool = new SetOnce<>();

    @Before
    public void setUpAction() {
        threadPool.set(new TestThreadPool(getTestName()));
        controller().registerHandler(new RestStreamInferenceAction(threadPool));
    }

    @After
    public void tearDownAction() {
        terminate(threadPool.get());

    }

    public void testStreamIsTrue() {
        SetOnce<Boolean> executeCalled = new SetOnce<>();
        verifyingClient.setExecuteVerifier(((actionType, actionRequest) -> {
            assertThat(actionRequest, instanceOf(InferenceAction.Request.class));

            var request = (InferenceAction.Request) actionRequest;
            assertThat(request.isStreaming(), is(true));

            executeCalled.set(true);
            return createResponse();
        }));

        RestRequest inferenceRequest = new FakeRestRequest.Builder(xContentRegistry()).withMethod(RestRequest.Method.POST)
            .withPath("_inference/test/_stream")
            .withContent(new BytesArray("{}"), XContentType.JSON)
            .build();
        dispatchRequest(inferenceRequest);
        assertThat(executeCalled.get(), equalTo(true));
    }
}
