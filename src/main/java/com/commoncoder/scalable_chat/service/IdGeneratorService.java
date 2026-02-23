package com.commoncoder.scalable_chat.service;

/** Service for generating unique, time-sortable 64-bit IDs. */
public interface IdGeneratorService {
  /** Generates a new unique ID. */
  long nextId();
}
