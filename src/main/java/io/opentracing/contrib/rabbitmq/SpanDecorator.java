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
import io.opentracing.Span;
import io.opentracing.tag.Tags;

import java.util.function.Consumer;

public class SpanDecorator {
  public static final String COMPONENT_NAME = "java-rabbitmq";

  public static void onRequest(String exchange, Span span) {
    Tags.COMPONENT.set(span, COMPONENT_NAME);
    Tags.MESSAGE_BUS_DESTINATION.set(span, exchange);
  }

  public static void onResponse(Span span) {
    Tags.COMPONENT.set(span, COMPONENT_NAME);
  }

  private static <A> void notNull(A value, Consumer<String> action) {
    if (value != null) action.accept(value.toString());
  }

  private static void notNull(String value, Consumer<String> action) {
    if (value != null) action.accept(value);
  }

  public static void addProperties(Span span, AMQP.BasicProperties properties) {
    notNull(properties.getUserId(),          v -> span.setTag("user.id", v));
    notNull(properties.getContentType(),     v -> span.setTag("content.type", v));
    notNull(properties.getContentEncoding(), v -> span.setTag("content.encoding", v));
    notNull(properties.getMessageId(),       v -> span.setTag("message.id", v));
    notNull(properties.getCorrelationId(),   v -> span.setTag("correlation.id", v));
    notNull(properties.getDeliveryMode(),    v -> span.setTag("delivery.mode", v));
    notNull(properties.getReplyTo(),         v -> span.setTag("reply.to", v));
    notNull(properties.getPriority(),        v -> span.setTag("priority", v));

    span.setTag("body.size", properties.getBodySize());

    if (properties.getHeaders() != null) {
      properties
              .getHeaders()
              .forEach((key, value) -> span.setTag("header." + key, value.toString()));
    }
  }
}
