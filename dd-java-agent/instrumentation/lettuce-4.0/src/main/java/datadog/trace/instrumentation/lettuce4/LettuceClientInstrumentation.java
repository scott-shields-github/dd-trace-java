package datadog.trace.instrumentation.lettuce4;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class LettuceClientInstrumentation extends Instrumenter.Default {

  public LettuceClientInstrumentation() {
    super("lettuce");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.RedisClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPrivate())
            .and(nameStartsWith("connect"))
            .and(returns(named("com.lambdaworks.redis.api.StatefulConnection")))
            .and(takesArgument(0, named("com.lambdaworks.redis.RedisURI"))),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        packageName + ".LettuceConnectionAdvice");
  }
}
