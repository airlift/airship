package io.airlift.airship.shared;

import java.io.InputStream;

/**
 */
public interface WritableRepository extends Repository
{
  public void put(String key, InputStream inputStream);
}
