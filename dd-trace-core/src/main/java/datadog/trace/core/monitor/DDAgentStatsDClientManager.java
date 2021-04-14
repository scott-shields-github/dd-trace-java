package datadog.trace.core.monitor;

import static datadog.trace.bootstrap.instrumentation.api.WriterConstants.LOGGING_WRITER_TYPE;

import datadog.trace.api.Config;
import datadog.trace.api.Function;
import datadog.trace.api.Functions;
import datadog.trace.api.StatsDClient;
import datadog.trace.api.StatsDClientManager;
import datadog.trace.api.cache.DDCache;
import datadog.trace.api.cache.DDCaches;

public final class DDAgentStatsDClientManager implements StatsDClientManager {
  private static final DDAgentStatsDClientManager INSTANCE = new DDAgentStatsDClientManager();

  private static final boolean USE_LOGGING_CLIENT =
      LOGGING_WRITER_TYPE.equals(Config.get().getWriterType());

  public static StatsDClientManager statsDClientManager() {
    return INSTANCE;
  }

  private final DDCache<String, DDAgentStatsDConnection> connectionPool =
      DDCaches.newUnboundedCache(4);

  @Override
  public StatsDClient statsDClient(
      final String host, final int port, final String namespace, final String[] constantTags) {
    Function<String, String> nameMapping = Functions.<String>zero();
    Function<String[], String[]> tagMapping = Functions.<String[]>zero();

    if (null != namespace) {
      nameMapping = new NameResolver(namespace);
    }

    if (null != constantTags && constantTags.length > 0) {
      tagMapping = new TagCombiner(constantTags);
    }

    if (USE_LOGGING_CLIENT) {
      return new LoggingStatsDClient(host, port, nameMapping, tagMapping);
    } else {
      return new DDAgentStatsDClient(getConnection(host, port), nameMapping, tagMapping);
    }
  }

  private DDAgentStatsDConnection getConnection(final String host, final int port) {
    String connectionKey = "statsd:" + host + ':' + port;
    return connectionPool.computeIfAbsent(
        connectionKey,
        new Function<String, DDAgentStatsDConnection>() {
          @Override
          public DDAgentStatsDConnection apply(final String unused) {
            return new DDAgentStatsDConnection(host, port);
          }
        });
  }

  /** Resolves metrics names by prepending a namespace prefix. */
  static final class NameResolver implements Function<String, String> {
    private final DDCache<String, String> resolvedNames = DDCaches.newFixedSizeCache(32);
    private final Function<String, String> namePrefixer;

    NameResolver(final String namespace) {
      this.namePrefixer =
          new Function<String, String>() {
            private final String prefix = namespace + '.';

            @Override
            public String apply(final String metricName) {
              return prefix + metricName;
            }
          };
    }

    @Override
    public String apply(final String metricName) {
      return resolvedNames.computeIfAbsent(metricName, namePrefixer);
    }
  }

  /** Combines per-call metrics tags with pre-packed constant tags. */
  static final class TagCombiner implements Function<String[], String[]> {
    private final DDCache<String[], String[]> combinedTags = DDCaches.newFixedSizeArrayKeyCache(64);
    // single-element array containing the pre-packed constant tags
    private final String[] packedTags;
    private final Function<String[], String[]> tagsInserter;

    public TagCombiner(final String[] constantTags) {
      this.packedTags = pack(constantTags);
      this.tagsInserter =
          new Function<String[], String[]>() {
            @Override
            public String[] apply(final String[] tags) {
              // extend per-call array by one to add the pre-packed constant tags
              String[] result = new String[tags.length + 1];
              System.arraycopy(tags, 0, result, 1, tags.length);
              result[0] = packedTags[0];
              return result;
            }
          };
    }

    @Override
    public String[] apply(final String[] tags) {
      if (null == tags || tags.length == 0) {
        return packedTags; // no per-call tags so we can use the pre-packed array
      } else {
        return combinedTags.computeIfAbsent(tags, tagsInserter);
      }
    }

    /**
     * Packs the constant tags into a single element array using the same separator as DogStatsD.
     */
    private static String[] pack(final String[] tags) {
      StringBuilder buf = new StringBuilder(tags[0]);
      for (int i = 1; i < tags.length; i++) {
        buf.append(',').append(tags[i]);
      }
      return new String[] {buf.toString()};
    }
  }
}
