/*
 * Copyright 2017-2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.rabbitmq;

import com.rabbitmq.client.AMQP;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import java.util.HashMap;
import java.util.Map;

public class TracingUtils {

  public static SpanContext extract(AMQP.BasicProperties props, Tracer tracer) {
    SpanContext spanContext = tracer
        .extract(Format.Builtin.TEXT_MAP, new HeadersMapExtractAdapter(props.getHeaders()));
    if (spanContext != null) {
      return spanContext;
    }

    Span span = tracer.activeSpan();
    if (span != null) {
      return span.context();
    }
    return null;
  }

  public static void buildAndFinishChildSpan(AMQP.BasicProperties props, String queue,
      Tracer tracer, String routingKey) {
    Span child = buildChildSpan(props, queue, tracer, routingKey);
    if (child != null) {
      child.finish();
    }
  }

  public static Span buildChildSpan(AMQP.BasicProperties props, String queue, Tracer tracer, String routingKey) {
    Tracer.SpanBuilder spanBuilder = tracer.buildSpan("receive")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER);

    if (queue != null) {
      spanBuilder.withTag("queue", queue);
    }

    SpanContext parentContext = TracingUtils.extract(props, tracer);
    if (parentContext != null) {
      spanBuilder.addReference(References.FOLLOWS_FROM, parentContext);
    }

    Span span = spanBuilder.start();
    SpanDecorator.onResponse(span);
    SpanDecorator.addProperties(span, props);
    if (routingKey != null) span.setTag("routingKey", routingKey);

    try {
      if (props.getHeaders() != null) {
        tracer.inject(span.context(), Builtin.TEXT_MAP,
            new HeadersMapInjectAdapter(props.getHeaders()));
      }
    } catch (Exception e) {
      // Ignore. Headers can be immutable. Waiting for a proper fix.
    }

    return span;
  }

  public static Span buildSpan(String exchange, String routingKey, AMQP.BasicProperties props,
      Tracer tracer) {
    Tracer.SpanBuilder spanBuilder = tracer.buildSpan("send")
        .ignoreActiveSpan()
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER)
        .withTag("routingKey", routingKey);

    SpanContext spanContext = null;

    if (props != null && props.getHeaders() != null) {
      // just in case if span context was injected manually to props in basicPublish
      spanContext = tracer.extract(Format.Builtin.TEXT_MAP,
          new HeadersMapExtractAdapter(props.getHeaders()));
    }

    if (spanContext == null) {
      Span parentSpan = tracer.activeSpan();
      if (parentSpan != null) {
        spanContext = parentSpan.context();
      }
    }

    if (spanContext != null) {
      spanBuilder.asChildOf(spanContext);
    }

    Span span = spanBuilder.start();
    SpanDecorator.onRequest(exchange, span);
    if (props != null) SpanDecorator.addProperties(span, props);

    return span;
  }

  public static AMQP.BasicProperties inject(AMQP.BasicProperties properties, Span span,
      Tracer tracer) {

    // Headers of AMQP.BasicProperties is unmodifiableMap therefore we build new AMQP.BasicProperties
    // with injected span context into headers
    Map<String, Object> headers = new HashMap<>();

    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new HeadersMapInjectAdapter(headers));

    if (properties == null) {
      return new AMQP.BasicProperties().builder().headers(headers).build();
    }

    if (properties.getHeaders() != null) {
      headers.putAll(properties.getHeaders());
    }

    return properties.builder()
        .headers(headers)
        .build();
  }
}
