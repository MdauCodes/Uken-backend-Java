package com.mdau.ukena.pos.dto;

/** Polled by the POS screen (~every 20s) so an operator finds out the reader is
 *  offline/busy before a customer is standing there, instead of only discovering
 *  it when a charge fails. actionStatus/actionFailureCode are null when the
 *  reader has no in-flight action. */
public record PosReaderStatus(
        boolean online,
        String label,
        String actionStatus,
        String actionFailureCode
) {}
