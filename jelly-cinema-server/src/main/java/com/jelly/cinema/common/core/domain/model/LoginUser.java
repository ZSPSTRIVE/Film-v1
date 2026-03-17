package com.jelly.cinema.common.core.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serial;
import java.io.Serializable;

/**
 * Backward-compatible placeholder for legacy Sa-Token session data that still
 * references the old LoginUser package name in Redis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
