package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.proofpoint.configuration.ConfigurationAwareModule;
import com.proofpoint.configuration.ConfigurationFactory;

public class ConditionalModule implements ConfigurationAwareModule
{
    public static ConfigurationAwareModule installIfPropertyDefined(Module module, String property)
    {
        return new ConditionalModule(module, property, null);
    }

    public static ConfigurationAwareModule installIfPropertyEquals(Module module, String property, String expectedValue)
    {
        return new ConditionalModule(module, property, expectedValue);
    }

    private final Module module;
    private final String property;
    private final String expectedValue;
    private ConfigurationFactory configurationFactory;

    private ConditionalModule(Module module, String property, String expectedValue)
    {
        Preconditions.checkNotNull(module, "module is null");
        Preconditions.checkNotNull(property, "property is null");

        this.module = module;
        this.property = property;
        this.expectedValue = expectedValue;
    }

    @Override
    public void setConfigurationFactory(ConfigurationFactory configurationFactory)
    {
        this.configurationFactory = configurationFactory;
        configurationFactory.consumeProperty(property);

        // consume properties if we are not going to install the module
        if (!shouldInstall()) {
            configurationFactory.registerConfigurationClasses(module);
        }
    }

    @Override
    public void configure(Binder binder)
    {
        Preconditions.checkNotNull(configurationFactory, "configurationFactory is null");
        if (shouldInstall()) {
            binder.install(module);
        }
    }

    private boolean shouldInstall()
    {
        if (expectedValue != null) {
            return expectedValue.equals(configurationFactory.getProperties().get(property));
        }
        else {
            return configurationFactory.getProperties().containsKey(property);
        }
    }
}
