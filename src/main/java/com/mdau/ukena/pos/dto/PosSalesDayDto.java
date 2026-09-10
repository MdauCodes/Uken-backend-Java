package com.mdau.ukena.pos.dto;

import java.time.LocalDate;
import java.util.UUID;

/** One calendar day's worth of completed market-stall sales — see
 *  PosService.salesByDate. marketDayNumber counts only qualifying days, oldest
 *  first, so it stays stable as new days are added; null on a day that isn't one.
 *  manualOverride is true when marketDay is true only because an admin flagged
 *  it by hand (see MarketDay), not because it hit the sales threshold — the
 *  frontend uses it to decide whether an "un-mark" action makes sense. name/
 *  catalogueId/catalogueName are only ever set on a manually flagged/planned day
 *  (null on a purely auto-detected one). */
public record PosSalesDayDto(
        LocalDate date,
        long orderCount,
        long totalPence,
        boolean marketDay,
        Integer marketDayNumber,
        boolean manualOverride,
        String name,
        UUID catalogueId,
        String catalogueName
) {}
