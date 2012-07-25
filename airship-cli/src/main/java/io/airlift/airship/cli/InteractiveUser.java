package com.proofpoint.galaxy.cli;

public interface InteractiveUser
{
    boolean ask(String question, boolean defaultValue);
}
