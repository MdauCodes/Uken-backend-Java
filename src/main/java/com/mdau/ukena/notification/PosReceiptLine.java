package com.mdau.ukena.notification;

/** One line of a market-stall receipt email — see EmailService.sendPosReceipt. */
public record PosReceiptLine(String name, int quantity, int pricePence) {}
