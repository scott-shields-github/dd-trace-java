package datadog.trace.instrumentation.lettuce4;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// @AutoService(Instrumenter.class)
public class LettuceReactiveCommandsInstrumentation extends Instrumenter.Default {

  public LettuceReactiveCommandsInstrumentation() {
    super("lettuce", "lettuce-4-rx");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.AbstractRedisReactiveCommands");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("createObservable"))
            .and(takesArgument(0, named("java.util.function.Supplier")))
            .and(returns(named("rx.Observable"))),
        packageName + ".rx.LettuceObservableFromSupplierAdvice");
    transformers.put(
        isMethod()
            .and(named("createDissolvingObservable"))
            .and(takesArgument(0, named("java.util.function.Supplier")))
            .and(returns(named("rx.Observable"))),
        packageName + ".rx.LettuceDissolvingObservableFromSupplierAdvice");
    transformers.put(
        isMethod()
            .and(named("createDissolvingObservable"))
            .and(takesArgument(0, named("com.lambdaworks.redis.protocol.CommandType")))
            .and(returns(named("rx.Observable"))),
        packageName + ".rx.LettuceDissolvingObservableFromCommandAdvice");

    return transformers;
  }
}
