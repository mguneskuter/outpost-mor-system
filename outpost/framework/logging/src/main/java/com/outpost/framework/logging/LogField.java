package com.outpost.framework.logging;

/**
 * A closed description of one structured-log field.
 *
 * <p>Business modules own implementations of this interface. The framework only uses the stable
 * JSON key and never accepts caller-supplied key names, maps, request bodies, or domain objects.
 */
public interface LogField {

  /** Returns the stable JSON key owned by the implementing module. */
  String getJsonKey();
}
