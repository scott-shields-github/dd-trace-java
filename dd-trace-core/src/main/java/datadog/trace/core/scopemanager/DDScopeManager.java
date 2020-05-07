package datadog.trace.core.scopemanager;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.context.TraceScope;

/**
 * Allows custom scope managers. See OTScopeManager, CustomScopeManager, and ContextualScopeManager
 */
public interface DDScopeManager {
  AgentScope activate(AgentSpan span);

  /** @deprecated use {@link #activate(AgentSpan)} instead. */
  @Deprecated
  AgentScope activate(AgentSpan span, boolean finishOnClose);

  /** @deprecated Scopes should be tracked directly by the instrumentation. */
  @Deprecated
  TraceScope active();

  AgentSpan activeSpan();
}
