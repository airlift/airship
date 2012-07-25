package io.airlift.airship.cli;

public interface InteractiveUser
{
    boolean ask(String question, boolean defaultValue);
}
