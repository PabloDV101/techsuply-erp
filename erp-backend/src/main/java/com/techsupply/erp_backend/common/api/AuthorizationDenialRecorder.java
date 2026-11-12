package com.techsupply.erp_backend.common.api;

/** Port used by HTTP error handling without depending on an audit implementation. */
public interface AuthorizationDenialRecorder {
    void deniedAuthorization();
}
