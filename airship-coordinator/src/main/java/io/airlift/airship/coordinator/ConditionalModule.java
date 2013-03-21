package io.airlift.airship.coordinator;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.configuration.ConfigurationAwareModule;
import io.airlift.configuration.ConfigurationFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

class ConditionalModule
        implements ConfigurationAwareModule
{
    public static ConditionalModule installIfPropertyEquals(Module module, String property, String expectedValue)
    {
        return new ConditionalModule(module, property, expectedValue);
    }

    private final Module module;
    private final String property;
    private final String expectedValue;
    private ConfigurationFactory configurationFactory;
    private boolean allowUnset = false;

    private ConditionalModule(Module module, String property, String expectedValue)
    {
        this.module = checkNotNull(module, "module is null");
        this.property = checkNotNull(property, "property is null");
        this.expectedValue = checkNotNull(expectedValue, "expectedValue is null");
    }

    public ConditionalModule withAllowUnset(boolean allowUnset)
    {
      this.allowUnset = allowUnset;
      return this;
    }

    @Override
    public void setConfigurationFactory(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = checkNotNull(configurationFactory, "configurationFactory is null");
        configurationFactory.consumeProperty(property);
    }

    @Override
    public void configure(Binder binder)
    {
        checkState(configurationFactory != null, "configurationFactory was not set");
        if (!configurationFactory.getProperties().containsKey(property)) {
            binder.addError("Required configuration property '%s' was not set", property);
        }
        if (expectedValue.equals(configurationFactory.getProperties().get(property))) {
            binder.install(module);
        }
    }
}
