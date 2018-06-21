package org.kairosdb.core.opentracing;

import io.opentracing.*;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

import javax.ws.rs.core.HttpHeaders;

public class openTracingUtils {

    public io.opentracing.Scope startSpanWithContext(String spanName, SpanContext spanContext, HttpHeaders httpHeaders, Tracer tracer) {
        ScopeManager scopeManager = tracer.scopeManager();
        SpanContext spanContext1 = tracer.extract(Format.Builtin.HTTP_HEADERS, new HttpHeadersCarrier(httpHeaders.getRequestHeaders()));
        Tracer.SpanBuilder spanBuild = tracer.buildSpan(spanName).withTag("Name",spanName).withTag(Tags.SPAN_KIND.getKey(),Tags.SPAN_KIND_SERVER);
        if (spanContext != null)
            spanBuild.asChildOf(spanContext);

        Span span = spanBuild.start();
        return scopeManager.activate(span, true);
    }
}
