package com.commoncoder.scalable_chat.service;

/** Provides a unique node ID for the current server instance. */
public interface NodeIdProvider {
  /** Returns a unique, sequential node ID. Typically used for Snowflake ID generation. */
  int getNodeId();
}
