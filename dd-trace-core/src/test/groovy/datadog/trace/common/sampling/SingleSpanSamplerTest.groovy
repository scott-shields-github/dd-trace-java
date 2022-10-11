package datadog.trace.common.sampling

import datadog.trace.api.Config
import datadog.trace.common.writer.ListWriter
import datadog.trace.core.DDSpan
import datadog.trace.core.test.DDCoreSpecification

import static datadog.trace.api.config.TracerConfig.SPAN_SAMPLING_RULES
import static datadog.trace.api.sampling.SamplingMechanism.SPAN_SAMPLING_RATE

class SingleSpanSamplerTest extends DDCoreSpecification {

  def "Single Span Sampler is not created when no rules provided"() {
    given:
    Properties properties = new Properties()
    if (rules != null) {
      properties.setProperty(SPAN_SAMPLING_RULES, rules)
    }

    when:
    SingleSpanSampler sampler = SingleSpanSampler.Builder.forConfig(Config.get(properties))

    then:
    sampler == null

    where:
    rules << [
      null,
      "[]",
      """[ {} ]""",
      """[ { "service": "*", "name": "*", "sample_rate": 10.0 } ]""",
      """[ { "service": "*", "name": "*" } ]""",
      """[ { "service": "*", "name": "*", "sample_rate": 1.0, "max_per_second": "N/A" } ]"""
    ]
  }

  def "Single Span Sampler set sampling priority"() {
    given:
    Properties properties = new Properties()
    if (rules != null) {
      properties.setProperty(SPAN_SAMPLING_RULES, rules)
    }
    def tracer = tracerBuilder().writer(new ListWriter()).build()

    when:
    SingleSpanSampler sampler = SingleSpanSampler.Builder.forConfig(Config.get(properties))

    then:
    sampler instanceof SingleSpanSampler

    when:
    DDSpan span = tracer.buildSpan("operation")
      .withServiceName("service")
      .withTag("env", "bar")
      .ignoreActiveSpan().start()

    then:
    sampler.setSamplingPriority(span) == isFirstSampled

    span.getTag("_dd.span_sampling.mechanism") == expectedMechanism
    span.getTag("_dd.span_sampling.rule_rate") == expectedRate
    span.getTag("_dd.span_sampling.max_per_second") == expectedLimit

    where:
    rules                                                                                             | isFirstSampled | expectedMechanism  | expectedRate | expectedLimit
    """[ { "service": "*", "name": "*", "sample_rate": 1.0 } ]"""                                     | true           | SPAN_SAMPLING_RATE | 1.0          | null
    """[ { "service": "*", "name": "*", "sample_rate": 1.0, "max_per_second": 10 } ]"""               | true           | SPAN_SAMPLING_RATE | 1.0          | 10
    """[ { "service": "ser*", "name": "oper*", "sample_rate": 1.0, "max_per_second": 15 } ]"""        | true           | SPAN_SAMPLING_RATE | 1.0          | 15
    """[ { "service": "?ervice", "name": "operati?n", "sample_rate": 1.0, "max_per_second": 10 } ]""" | true           | SPAN_SAMPLING_RATE | 1.0          | 10
    """[ { "service": "service-b", "name": "*", "sample_rate": 1.0, "max_per_second": 10 } ]"""       | false          | null               | null         | null
    """[ { "service": "*", "name": "*", "sample_rate": 0.0 } ]"""                                     | false          | null               | null         | null
    """[ { "service": "*", "name": "operation-b", "sample_rate": 0.5 } ]"""                           | false          | null               | null         | null
  }

  def "Single Span Sampler set sampling priority with the max-per-second limit"() {
    given:
    Properties properties = new Properties()
    if (rules != null) {
      properties.setProperty(SPAN_SAMPLING_RULES, rules)
    }
    def tracer = tracerBuilder().writer(new ListWriter()).build()

    when:
    SingleSpanSampler sampler = SingleSpanSampler.Builder.forConfig(Config.get(properties))

    then:
    sampler instanceof SingleSpanSampler

    when:
    DDSpan span1 = tracer.buildSpan("operation")
      .withServiceName("service")
      .withTag("env", "bar")
      .ignoreActiveSpan().start()

    DDSpan span2 = tracer.buildSpan("operation")
      .withServiceName("service")
      .withTag("env", "bar")
      .ignoreActiveSpan().start()

    then:
    sampler.setSamplingPriority(span1) == isFirstSampled
    sampler.setSamplingPriority(span2) == isSecondSampled

    where:
    rules                                                                                            | isFirstSampled | isSecondSampled
    """[ { "service": "*", "name": "*", "sample_rate": 1.0, "max_per_second": 1 } ]"""               | true           | false
    """[ { "service": "ser*", "name": "oper*", "sample_rate": 1.0, "max_per_second": 1 } ]"""        | true           | false
    """[ { "service": "?ervice", "name": "operati?n", "sample_rate": 1.0, "max_per_second": 1 } ]""" | true           | false

    """[ { "service": "*", "name": "*", "sample_rate": 1.0, "max_per_second": 2 } ]"""               | true           | true
    """[ { "service": "ser*", "name": "oper*", "sample_rate": 1.0, "max_per_second": 2 } ]"""        | true           | true
    """[ { "service": "?ervice", "name": "operati?n", "sample_rate": 1.0, "max_per_second": 2 } ]""" | true           | true
  }
}
