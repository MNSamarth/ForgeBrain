/**
 * HTTP controllers. Only {@link com.forgebrain.backend.controllers.HealthController} exists
 * in this phase — every pipeline-stage endpoint (topic selection, pipeline run status, and so
 * on) is deliberately not wired up yet, since a controller calling an unimplemented {@link
 * com.forgebrain.backend.services} interface would either fail to start or be a fake
 * implementation, both of which this phase's rules exclude. See TODO.md.
 */
package com.forgebrain.backend.controllers;
